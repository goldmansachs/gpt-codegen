package org.rj.modelgen.ui.component;

import java.util.Map;

import java.util.List;

/**
 * Definition of an A2UI function
 */
public class A2UIFunctionDefinition {
    private final String name;
    private final String description;
    private final Map<String, String> args;         // arg name -> type
    private final List<String> requiredArgs;         // which args are required
    private final String returnType;

    public A2UIFunctionDefinition(String name, String description, Map<String, String> args,
                                  List<String> requiredArgs, String returnType) {
        this.name = name;
        this.description = description;
        this.args = args;
        this.requiredArgs = requiredArgs;
        this.returnType = returnType;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, String> getArgs() {
        return args;
    }

    public List<String> getRequiredArgs() {
        return requiredArgs;
    }

    public String getReturnType() {
        return returnType;
    }
}
