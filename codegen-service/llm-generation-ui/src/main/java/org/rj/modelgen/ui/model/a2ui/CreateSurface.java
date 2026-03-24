package org.rj.modelgen.ui.model.a2ui;

import java.util.Map;

public record CreateSurface(String surfaceId, String catalogId, boolean sendDataModel, Map<String, Object> theme) {}

