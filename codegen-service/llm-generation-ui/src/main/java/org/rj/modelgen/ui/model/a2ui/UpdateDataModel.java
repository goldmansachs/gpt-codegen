package org.rj.modelgen.ui.model.a2ui;

public record UpdateDataModel(
        String surfaceId,
        String path,       // optional - JSON Pointer (RFC 6901), defaults to "/"
        Object value       // optional - any JSON value; if omitted, the key at path is removed
) {}
