package org.rj.modelgen.service.beans;

public class UIGenerationSessionData {
    private final String id;
    private String currentIntentData;
    private String currentUIData;

    public UIGenerationSessionData(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getCurrentUIData() {
        return currentUIData;
    }

    public void setCurrentUIData(String currentUIData) {
        this.currentUIData = currentUIData;
    }

    public String getCurrentIntentData() {
        return currentIntentData;
    }

    public void setCurrentIntentData(String currentIntentData) {
        this.currentIntentData = currentIntentData;
    }
}

