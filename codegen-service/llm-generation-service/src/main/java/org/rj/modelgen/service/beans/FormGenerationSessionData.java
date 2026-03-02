package org.rj.modelgen.service.beans;

public class FormGenerationSessionData {
    private final String id;
    private String currentIntentData;
    private String currentA2UIData;

    public FormGenerationSessionData(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getCurrentA2UIData() {
        return currentA2UIData;
    }

    public void setCurrentA2UIData(String currentA2UIData) {
        this.currentA2UIData = currentA2UIData;
    }

    public String getCurrentIntentData() {
        return currentIntentData;
    }

    public void setCurrentIntentData(String currentIntentData) {
        this.currentIntentData = currentIntentData;
    }
}

