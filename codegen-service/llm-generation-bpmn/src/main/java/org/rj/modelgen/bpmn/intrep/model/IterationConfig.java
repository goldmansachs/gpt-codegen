package org.rj.modelgen.bpmn.intrep.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"list", "itemInList", "itemLabel", "completionCondition"})
public class IterationConfig {

    private String list;
    private String itemInList;
    private String itemLabel;
    private String completionCondition;

    public IterationConfig() {
    }

    public IterationConfig(String list, String itemInList, String itemLabel) {
        this.list = list;
        this.itemInList = itemInList;
        this.itemLabel = itemLabel;
    }

    public String getList() {
        return list;
    }

    public void setList(String list) {
        this.list = list;
    }

    public String getItemInList() {
        return itemInList;
    }

    public void setItemInList(String itemInList) {
        this.itemInList = itemInList;
    }

    public String getItemLabel() {
        return itemLabel;
    }

    public void setItemLabel(String itemLabel) {
        this.itemLabel = itemLabel;
    }

    public String getCompletionCondition() {
        return completionCondition;
    }

    public void setCompletionCondition(String completionCondition) {
        this.completionCondition = completionCondition;
    }

    @JsonIgnore
    public boolean isConfigured() {
        return list != null && !list.isBlank()
                && itemInList != null && !itemInList.isBlank();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IterationConfig that = (IterationConfig) o;
        return Objects.equals(list, that.list) &&
                Objects.equals(itemInList, that.itemInList) &&
                Objects.equals(itemLabel, that.itemLabel) &&
                Objects.equals(completionCondition, that.completionCondition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(list, itemInList, itemLabel, completionCondition);
    }
}

