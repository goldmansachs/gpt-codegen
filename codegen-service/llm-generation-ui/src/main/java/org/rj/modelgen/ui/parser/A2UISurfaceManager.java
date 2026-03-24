package org.rj.modelgen.ui.parser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONPointer;
import org.json.JSONPointerException;
import org.rj.modelgen.llm.util.Util;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.CreateSurface;
import org.rj.modelgen.ui.model.a2ui.DeleteSurface;
import org.rj.modelgen.ui.model.a2ui.UpdateDataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class A2UISurfaceManager {
    private static final Logger LOG = LoggerFactory.getLogger(A2UISurfaceManager.class);

    private final A2UIComponentParser parser = new A2UIComponentParser();

    // Per-surface state: surfaceId -> component map
    private final Map<String, Map<String, A2UIComponent<?>>> surfaces =
            new ConcurrentHashMap<>();

    // Per-surface data models: surfaceId -> data model
    private final Map<String, Map<String, Object>> dataModels =
            new ConcurrentHashMap<>();

    // Per-surface metadata
    private final Map<String, CreateSurface> surfaceMetadata =
            new ConcurrentHashMap<>();

    /**
     * Processes a batch of JSONL messages (as parsed JsonObjects).
     * Each message is an A2UI envelope with exactly one of the four keys.
     */
    public void processMessages(List<JSONObject> messages) {
        for (JSONObject message : messages) {
            if (message.has("createSurface")) {
                handleCreateSurface(
                        Util.deserializeJsonOrThrow(message.getJSONObject("createSurface"),
                                CreateSurface.class)
                );
            } else if (message.has("updateComponents")) {
                handleUpdateComponents(message.getJSONObject("updateComponents"));
            } else if (message.has("updateDataModel")) {
                handleUpdateDataModel(
                        Util.deserializeJsonOrThrow(message.getJSONObject("updateDataModel"),
                                UpdateDataModel.class)
                );
            } else if (message.has("deleteSurface")) {
                handleDeleteSurface(
                        Util.deserializeJsonOrThrow(message.getJSONObject("deleteSurface"),
                                DeleteSurface.class)
                );
            }
        }
    }

    private void handleCreateSurface(CreateSurface create) {
        surfaceMetadata.put(create.surfaceId(), create);
        surfaces.put(create.surfaceId(), new HashMap<>());
        dataModels.put(create.surfaceId(), new HashMap<>());
    }

    private void handleUpdateComponents(JSONObject updateComponentsJson) {
        String surfaceId = updateComponentsJson.getString("surfaceId");

        Map<String, A2UIComponent<?>> componentMap = surfaces.get(surfaceId);
        if (componentMap == null) {
            LOG.error("Received updateComponents for unknown surface: [{}]", surfaceId);
            return;
        }

        // Extract the components array as raw JsonObjects
        List<JSONObject> rawComponents = new java.util.ArrayList<>();
        updateComponentsJson.getJSONArray("components").forEach(element ->
                rawComponents.add((JSONObject) element)
        );

        // Parse and merge into the existing component map
        // Per the spec: "components to be added to or updated"
        Map<String, A2UIComponent<?>> parsed = parser.parseComponents(rawComponents);
        componentMap.putAll(parsed);
    }

    private void handleUpdateDataModel(UpdateDataModel update) {
        // Implementation depends on JSON Pointer resolution
        // The spec says: replace value at path, or replace entire model if path is "/"
        Map<String, Object> dataModel = dataModels.get(update.surfaceId());
        if (dataModel == null) {
            LOG.error("Received updateDataModel for unknown surface: [{}]", update.surfaceId());
            return;
        }
        // Apply the update using JSON Pointer semantics
        applyDataModelUpdate(dataModel, update.path(), update.value());
    }

    private void handleDeleteSurface(DeleteSurface delete) {
        surfaces.remove(delete.surfaceId());
        dataModels.remove(delete.surfaceId());
        surfaceMetadata.remove(delete.surfaceId());
    }

    private void applyDataModelUpdate(Map<String, Object> model, String path, Object value) {
        // If path is null or "/", replace entire model
        if (path == null || path.equals("/")) {
            replaceEntireModel(model, value);
            return;
        }

        // Path must start with "/"
        if (!path.startsWith("/")) {
            LOG.error("Invalid JSON Pointer path (must start with '/'): {}", path);
            return;
        }

        // Derive the parent pointer and the final reference token
        int lastSlash = path.lastIndexOf('/');
        String parentPath = path.substring(0, lastSlash);
        String lastToken = path.substring(lastSlash + 1);

        // Unescape the final token per RFC 6901 (JSONPointer handles this for
        // intermediate segments, but we need the raw key/index for mutation)
        lastToken = lastToken.replace("~1", "/").replace("~0", "~");

        // Validate the parent path exists via JSONPointer against a JSONObject snapshot.
        // JSONPointer implements RFC 6901 navigation including escaping rules.
        if (!validateParentPath(model, parentPath, path)) {
            return;
        }

        // Resolve the live parent container and apply the mutation
        Object liveParent = resolveParent(model, parentPath);
        if (liveParent == null) {
            LOG.error("Path segment not found in live model for path '{}'", path);
            return;
        }

        applyMutation(liveParent, lastToken, value, path);
    }

    @SuppressWarnings("unchecked")
    private void replaceEntireModel(Map<String, Object> model, Object value) {
        model.clear();
        if (value == null) {
            return;
        }
        Object unwrapped = unwrapValue(value);
        if (unwrapped instanceof Map) {
            model.putAll((Map<String, Object>) unwrapped);
        } else {
            LOG.error("Cannot replace entire model with non-Map value: {}", unwrapped);
        }
    }

    private boolean validateParentPath(Map<String, Object> model, String parentPath, String fullPath) {
        if (parentPath.isEmpty()) {
            return true;
        }
        try {
            JSONObject jsonModel = new JSONObject(model);
            new JSONPointer(parentPath).queryFrom(jsonModel);
            return true;
        } catch (JSONPointerException e) {
            LOG.error("Path segment not found in path '{}': {}", fullPath, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private void applyMutation(Object parent, String token, Object value, String path) {
        if (parent instanceof Map) {
            applyMapUpdate((Map<String, Object>) parent, token, value);
        } else if (parent instanceof List) {
            applyListUpdate((List<Object>) parent, token, value, path);
        } else {
            LOG.error("Cannot navigate into non-container type at path '{}'", path);
        }
    }

    private void applyMapUpdate(Map<String, Object> parentMap, String token, Object value) {
        if (value == null) {
            parentMap.remove(token);
        } else {
            parentMap.put(token, unwrapValue(value));
        }
    }

    private void applyListUpdate(List<Object> parentList, String token, Object value, String path) {
        if ("-".equals(token)) {
            applyListAppend(parentList, value, path);
        } else {
            applyListIndexUpdate(parentList, token, value, path);
        }
    }

    private void applyListAppend(List<Object> parentList, Object value, String path) {
        if (value == null) {
            LOG.error("Cannot append null value to list with '-' token in path '{}'", path);
            return;
        }
        parentList.add(unwrapValue(value));
    }

    private void applyListIndexUpdate(List<Object> parentList, String token, Object value, String path) {
        int index = parseIndex(token, parentList.size());
        if (index < 0) {
            LOG.error("Invalid array index '{}' for list of size {} in path '{}'",
                    token, parentList.size(), path);
            return;
        }
        if (value == null) {
            parentList.remove(index);
        } else {
            parentList.set(index, unwrapValue(value));
        }
    }

    /**
     * Resolves the parent container in the live model (Map/List tree) by walking
     * the RFC 6901 reference tokens derived from {@code parentPath}.
     * Returns the live Map or List that the parent pointer refers to, or
     * {@code null} if the path cannot be resolved.
     */
    private Object resolveParent(Map<String, Object> model, String parentPath) {
        if (parentPath.isEmpty()) {
            return model;
        }

        // Split on '/' after stripping the leading '/' to obtain individual
        // reference tokens, then unescape each per RFC 6901.
        String stripped = parentPath.substring(1);
        String[] rawTokens = stripped.split("/", -1);

        Object current = model;
        for (String raw : rawTokens) {
            String token = raw.replace("~1", "/").replace("~0", "~");
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(token);
            } else if (current instanceof List) {
                int idx = parseIndex(token, ((List<?>) current).size());
                if (idx < 0) return null;
                current = ((List<?>) current).get(idx);
            } else {
                return null;
            }
            if (current == null) return null;
        }
        return current;
    }

    private int parseIndex(String token, int size) {
        try {
            int index = Integer.parseInt(token);
            return (index >= 0 && index < size) ? index : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private Object unwrapValue(Object value) {
        if (value instanceof JSONObject jsonObj) {
            return jsonObj.toMap();
        } else if (value instanceof JSONArray jsonArr) {
            return jsonArr.toList();
        }
        return value;
    }

    /**
     * Returns the component map for a given surface.
     * This is the flat adjacency list that the renderer uses
     * to reconstruct the tree starting from "root".
     */
    public Map<String, A2UIComponent<?>> getComponentMap(String surfaceId) {
        return surfaces.getOrDefault(surfaceId, Map.of());
    }

    /**
     * Returns the root component for a given surface, if available.
     */
    public Optional<A2UIComponent<?>> getRootComponent(String surfaceId) {
        Map<String, A2UIComponent<?>> map = surfaces.get(surfaceId);
        if (map == null) return Optional.empty();
        return Optional.ofNullable(map.get("root"));
    }

    /**
     * Returns the metadata for a given surface, if available.
     */
    public Optional<CreateSurface> getSurfaceMetadata(String surfaceId) {
        CreateSurface metadata = surfaceMetadata.get(surfaceId);
        if (metadata == null) return Optional.empty();
        return Optional.of(metadata);
    }

    /**
     * Returns the list of SurfaceIds stored in the top level map
     */
    public Set<String> getSurfaces() {
        return surfaces.keySet();
    }
}
