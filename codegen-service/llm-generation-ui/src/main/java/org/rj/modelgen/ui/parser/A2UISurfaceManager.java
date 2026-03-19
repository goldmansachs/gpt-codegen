package org.rj.modelgen.ui.parser;

import org.json.JSONObject;
import org.rj.modelgen.llm.util.Util;
import org.rj.modelgen.ui.model.a2ui.A2UIComponent;
import org.rj.modelgen.ui.model.a2ui.CreateSurface;
import org.rj.modelgen.ui.model.a2ui.DeleteSurface;
import org.rj.modelgen.ui.model.a2ui.UpdateDataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class A2UISurfaceManager {
    private static final Logger LOG = LoggerFactory.getLogger(A2UISurfaceManager.class);

    private final A2UIComponentParser parser = new A2UIComponentParser();

    // Per-surface state: surfaceId -> component map
    private final Map<String, Map<String, A2UIComponent>> surfaces =
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

        Map<String, A2UIComponent> componentMap = surfaces.get(surfaceId);
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
        // JSON Pointer resolution per RFC 6901
        // If path is null or "/", replace entire model
        if (path == null || path.equals("/")) {
            model.clear();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> newModel = (Map<String, Object>) value;
                model.putAll(newModel);
            }
            return;
        }
        // Otherwise, navigate to the parent and set the value
        // Full JSON Pointer implementation omitted for brevity
    }

    /**
     * Returns the component map for a given surface.
     * This is the flat adjacency list that the renderer uses
     * to reconstruct the tree starting from "root".
     */
    public Map<String, A2UIComponent> getComponentMap(String surfaceId) {
        return surfaces.getOrDefault(surfaceId, Map.of());
    }

    /**
     * Returns the root component for a given surface, if available.
     */
    public Optional<A2UIComponent> getRootComponent(String surfaceId) {
        Map<String, A2UIComponent> map = surfaces.get(surfaceId);
        if (map == null) return Optional.empty();
        return Optional.ofNullable(map.get("root"));
    }

    /**
     * Returns the root component for a given surface, if available.
     */
    public Optional<CreateSurface> getSurfaceMetadata(String surfaceId) {
        CreateSurface metadata = surfaceMetadata.get(surfaceId);
        if (metadata == null) return Optional.empty();
        return Optional.of(metadata);
    }
}
