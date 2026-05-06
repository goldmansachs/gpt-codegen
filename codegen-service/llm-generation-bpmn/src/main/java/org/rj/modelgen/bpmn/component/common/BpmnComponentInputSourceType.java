package org.rj.modelgen.bpmn.component.common;

public enum BpmnComponentInputSourceType {
    CONSTANT,
    SCRIPT,
    EXPRESSION;

    @Override
    public String toString() {
        return name();
    }
}
