package org.rj.modelgen.ui.model.a2ui;

public record A2UIMessage(
        String version,                    // "v0.9"
        CreateSurface createSurface,       // exactly one of these four will be non-null
        UpdateComponents updateComponents,
        UpdateDataModel updateDataModel,
        DeleteSurface deleteSurface
) {

    public MessageType getType() {
        if (createSurface != null) return MessageType.CREATE_SURFACE;
        if (updateComponents != null) return MessageType.UPDATE_COMPONENTS;
        if (updateDataModel != null) return MessageType.UPDATE_DATA_MODEL;
        if (deleteSurface != null) return MessageType.DELETE_SURFACE;
        throw new IllegalStateException("Unknown message type");
    }

    public enum MessageType {
        CREATE_SURFACE, UPDATE_COMPONENTS, UPDATE_DATA_MODEL, DELETE_SURFACE
    }
}