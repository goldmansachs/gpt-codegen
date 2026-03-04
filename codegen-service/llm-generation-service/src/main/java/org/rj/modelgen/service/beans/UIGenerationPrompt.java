package org.rj.modelgen.service.beans;

public class UIGenerationPrompt {
    private String prompt;
    private Double temperature;

    public UIGenerationPrompt() { }

    public UIGenerationPrompt(String prompt, double temperature) {
        this.prompt = prompt;
        this.temperature = temperature;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }
}

