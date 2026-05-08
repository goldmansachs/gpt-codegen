package org.rj.modelgen.bpmn.models.generation.validation;

import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariable;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.Patterns.*;

public class BpmnScriptUtils {

    private static final Logger LOG = Logger.getLogger(BpmnScriptUtils.class.getName());

    public static List<PayloadVariable> retrieveReadVariables(String inputValue) {
        List<PayloadVariable> variables = new ArrayList<>();
        Matcher matcher = VAR_READ_PATTERN.matcher(inputValue);
        while (matcher.find()) {
            String variableName = matcher.group(1);
            variables.add(new PayloadVariable(variableName, "string"));
        }
        return variables;
    }

    public static List<PayloadVariable> retrieveWriteVariables(String inputValue) {
        List<PayloadVariable> variables = new ArrayList<>();
        Matcher matcher = VAR_WRITE_PATTERN.matcher(inputValue);
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String variableValue = matcher.group(2);
            String variableType = matcher.group(3);
            variables.add(new PayloadVariable(variableName, variableType.toLowerCase()));
        }
        return variables;
    }

    public static List<PayloadVariable> retrieveGlobalVariables(String inputValue, BpmnGlobalVariableLibrary globalVariableLibrary) {
        List<PayloadVariable> variables = new ArrayList<>();
        Matcher matcher = GLOBAL_VAR_READ_PATTERN.matcher(inputValue);
        while (matcher.find()) {
            String variableName = matcher.group(1);
            String variableType = globalVariableLibrary.getVariableByName(variableName).map(BpmnGlobalVariable::getType).orElse("");
            variables.add(new PayloadVariable(variableName, variableType.toLowerCase()));
        }
        return variables;
    }

    // Resolves variable reads as payload variables. It ignores the source node ID and simply replaces the variable name with "payload.variableName".
    public static String resolveVariableReadsAsPayloadVar(String inputValue, boolean withInterpolation) {
        String replacement = withInterpolation ? "\\${payload.$1}" : "payload.$1";
        return inputValue.replaceAll(VAR_READ_PATTERN.pattern(), replacement);
    }

    // Resolves variable reads as payload variables or component outputs.
    public static String resolveVariableReads(String inputValue, BpmnComponentLibrary componentLibrary, boolean withInterpolation) {
        StringBuilder resolved = new StringBuilder();
        Matcher matcher = VAR_READ_PATTERN.matcher(inputValue);

        while (matcher.find()) {
            String variableName = matcher.group(1);

            // Search all components for generated outputs matching this variable name
            List<BpmnComponent.Variable> matchingOutputs = componentLibrary.getComponents().stream()
                    .filter(component -> component.getGeneratedOutputs() != null)
                    .flatMap(component -> component.getGeneratedOutputs().stream())
                    .filter(outputVar -> outputVar.getName().equals(variableName))
                    .toList();

            String replacement;
            if (!matchingOutputs.isEmpty()) {
                if (matchingOutputs.size() > 1) {
                    LOG.warning(String.format(
                            "Variable '%s' is a generated output of multiple components %s. Using the first match for resolution.",
                            variableName,
                            matchingOutputs.stream().map(BpmnComponent.Variable::getName).toList()));
                }
                String resolveValue = matchingOutputs.get(0).getResolveValue();
                replacement = withInterpolation ? "${" + resolveValue + "}" : resolveValue;
            } else {
                replacement = withInterpolation ? "${payload." + variableName + "}" : "payload." + variableName;
            }

            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    public static String resolveVariableWrites(String inputValue) {
        return inputValue.replaceAll(VAR_WRITE_PATTERN.pattern(), "payload.$1 = $2");
    }

    public static String resolveErrorThrows(String inputValue, String replacement) {
        return inputValue.replaceAll(THROW_ERROR_PATTERN.pattern(), replacement);
    }

    public static String resolveGlobalVariableReads(String inputValue, BpmnGlobalVariableLibrary globalVariableLibrary, boolean withInterpolation) {
        Matcher globalVarMatcher = GLOBAL_VAR_READ_PATTERN.matcher(inputValue);
        StringBuilder resolvedScript = new StringBuilder();
        while (globalVarMatcher.find()) {
            String varName = globalVarMatcher.group(1);
            String arguments = globalVarMatcher.group(2);

            var globalVar = globalVariableLibrary.getVariableByName(varName)
                    .orElseThrow(() -> new IllegalArgumentException("Global variable '" + varName + "' not found in library"));

            String resolveValue = globalVar.getResolveValue();

            // Replace argument placeholders with actual arguments
            if (arguments != null && !arguments.trim().isEmpty()) {
                String[] args = arguments.split(",");
                for (int i = 0; i < args.length; i++) {
                    resolveValue = resolveValue.replace("arg" + (i + 1), args[i].trim());
                }
            }
            if (withInterpolation) {
                resolveValue = "${" + resolveValue + "}";
            }
            globalVarMatcher.appendReplacement(resolvedScript, Matcher.quoteReplacement(resolveValue));
        }
        globalVarMatcher.appendTail(resolvedScript);
        return resolvedScript.toString();
    }

    public static String stripInterpolationSyntax(String expression) {
        if (expression.startsWith("${") && expression.endsWith("}")) {
            return expression.substring(2, expression.length() - 1); // Remove ${ and }
        }
        return expression;
    }

    public static String stripQuotes(String expression) {
        expression = expression.strip();
        if ((expression.startsWith("\"") && expression.endsWith("\"")) || (expression.startsWith("'") && expression.endsWith("'"))) {
            return expression.substring(1, expression.length() - 1);
        }
        return expression;
    }

    public static void applyFormatValueToAllInputs(List<ElementNodeInput> nodeInputs, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        if (nodeInputs == null) {
            return;
        }

        for (ElementNodeInput input : nodeInputs) {
            // Recursively process nested properties
            if (input.hasProperties()) {
                applyFormatValueToAllInputs(input.getProperties(), componentLibrary, globalVariableLibrary);
            } else {
                formatInputValue(input, componentLibrary, globalVariableLibrary);
            }
        }
    }

    private static void formatInputValue(ElementNodeInput input, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        String originalValue = input.getValue();

        if (originalValue == null || originalValue.isEmpty()) {
            return;
        }

        String formattedValue = formatValue(originalValue, componentLibrary, globalVariableLibrary);
        input.setValue(formattedValue);

        if (!originalValue.equals(formattedValue)) {
            if (isExpression(formattedValue)) {
                input.setVariableSource("EXPRESSION");
            } else {
                input.setVariableSource("SCRIPT");
            }
        }
    }

    /**
     * Determines if a value is a simple EXPRESSION or a complex SCRIPT.
     * EXPRESSION: Single-line with only getVariable() call
     * SCRIPT: Multi-line or contains setVariable
     */
    public static boolean isExpression(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }

        if (value.contains("\n")) {
            return false;
        }

        if (value.contains("setVariable(")) {
            return false;
        }
        return value.contains("getVariable(");
    }

    public static String formatValue(String inputValue, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        if (inputValue == null) return null;

        // 1. Global variables usage
        String result = reverseResolveGlobalVariables(inputValue, globalVariableLibrary);

        // 2. Automatically generated node outputs
        // ${takCompletionPayload.reasonCode} -> getVariable('reasonCode')
        result = reverseResolveNodeOutputs(result, componentLibrary);

        // 3. Error throws
        // throw new Exception(msg) -> throw(msg)
        result = reverseResolveErrorThrows(result);

        // 4. Payload Setters (Assignments) - MUST BE DONE BEFORE GETTERS
        // payload.x = y -> setVariable('x', y, 'Type')
        result = reverseResolveVariableWrites(result);

        // 5. Variable Interpolation with nested properties
        // ${payload.event.entity.namespace} -> getVariable('event.entity.namespace')
        // ${payload.x} -> getVariable('x')
        result = reverseResolveVariableReads(result);

        return result;
    }

    private static String reverseResolveVariableWrites(String inputValue) {
        return VAR_PAYLOAD_WRITE_PATTERN
                .matcher(inputValue)
                .replaceAll(mr -> {
                    String field = mr.group(1);
                    String value = mr.group(2).trim();
                    String type = inferType(value);
                    return String.format("setVariable('%s', %s, '%s')", field, value, type);
                });
    }

    private static String reverseResolveVariableReads(String inputValue) {
        String result = VAR_INTERPOLATED_PAYLOAD_READ_PATTERN
                .matcher(inputValue)
                .replaceAll(mr -> String.format("getVariable('%s')", mr.group(1)));
        return VAR_PAYLOAD_READ_PATTERN
                .matcher(result)
                .replaceAll(mr -> String.format("getVariable('%s')", mr.group(1)));
    }

    private static String reverseResolveGlobalVariables(String inputValue, BpmnGlobalVariableLibrary globalVariableLibrary) {
        if (inputValue == null || inputValue.isEmpty() || globalVariableLibrary == null) {
            return inputValue;
        }

        String result = inputValue;

        // Sort global variables by resolveValue length descending to match longer (more specific) patterns first
        List<BpmnGlobalVariable> sortedVars = globalVariableLibrary.getComponents().stream()
                .filter(gv -> gv.getResolveValue() != null && !gv.getResolveValue().isEmpty())
                .sorted((a, b) -> Integer.compare(b.getResolveValue().length(), a.getResolveValue().length()))
                .toList();

        for (var globalVar : sortedVars) {
            String resolveValue = globalVar.getResolveValue();
            String varName = globalVar.getName();
            List<BpmnGlobalVariable.GlobalVariableArgument> args = globalVar.getArguments();
            boolean hasArgs = args != null && !args.isEmpty();

            if (hasArgs) {
                // Build a regex from the resolveValue by replacing arg placeholders with capture groups
                // e.g. "engineUtils.parseJsonString(arg1)" -> "engineUtils\.parseJsonString\((.+?)\)"
                String regexPattern = Pattern.quote(resolveValue);
                for (int i = 0; i < args.size(); i++) {
                    String argPlaceholder = "arg" + (i + 1);
                    // Replace the quoted placeholder with a capture group
                    regexPattern = regexPattern.replace("\\Q" + argPlaceholder + "\\E", "\\E(.+?)\\Q");
                    // Also handle case where placeholder appears without separate quoting boundaries
                    regexPattern = regexPattern.replace(argPlaceholder, "\\E(.+?)\\Q");
                }
                // Clean up empty quote blocks
                regexPattern = regexPattern.replace("\\Q\\E", "");

                Pattern pattern = Pattern.compile(regexPattern);
                Matcher matcher = pattern.matcher(result);
                StringBuilder sb = new StringBuilder();

                while (matcher.find()) {
                    StringBuilder argList = new StringBuilder();
                    for (int i = 1; i <= matcher.groupCount(); i++) {
                        if (i > 1) argList.append(", ");
                        argList.append(matcher.group(i).trim());
                    }
                    String replacement = String.format("getGlobalVariable('%s', [%s])", varName, argList);
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                }
                matcher.appendTail(sb);
                result = sb.toString();
            } else {
                // Simple replacement for global variables without arguments
                result = result.replace("${" + resolveValue + "}", "getGlobalVariable('" + varName + "', [])");
                result = result.replace(resolveValue, "getGlobalVariable('" + varName + "', [])");
            }
        }

        return result;
    }

    private static String reverseResolveNodeOutputs(String inputValue, BpmnComponentLibrary componentLibrary) {
        if (inputValue == null || inputValue.isEmpty() || componentLibrary == null) {
            return inputValue;
        }

        // Collect all generated outputs across all components, sorted by resolveValue length descending to match longer (more specific) patterns first
        var allOutputs = componentLibrary.getComponents().stream()
                .filter(component -> component.getGeneratedOutputs() != null)
                .flatMap(component -> component.getGeneratedOutputs().stream())
                .filter(output -> output.getResolveValue() != null && !output.getResolveValue().isEmpty())
                .sorted((a, b) -> Integer.compare(b.getResolveValue().length(), a.getResolveValue().length()))
                .toList();

        String result = inputValue;
        for (var outputVar : allOutputs) {
            String resolveValue = outputVar.getResolveValue();
            String varName = outputVar.getName();

            // Handle interpolation syntax first: ${resolveValue} -> getVariable('varName')
            result = result.replace("${" + resolveValue + "}", "getVariable('" + varName + "')");
            // Handle plain resolveValue -> getVariable('varName')
            result = result.replace(resolveValue, "getVariable('" + varName + "')");
        }

        return result;
    }

    private static String reverseResolveErrorThrows(String inputValue) {
        // throw new Exception(msg) -> throw(msg)
        return inputValue.replaceAll("throw\\s+new\\s+Exception\\s*\\(\\s*(.+?)\\s*\\)", "throw($1)");
    }

    private static String inferType(String value) {
        if (value.matches("-?\\d+")) return "Integer";
        if (value.matches("-?\\d+\\.\\d+")) return "Double";
        if (value.equals("true") || value.equals("false")) return "Boolean";
        if (value.startsWith("[")) return "Array";
        if (value.startsWith("{")) return "Object";
        if (value.startsWith("\"") || value.startsWith("'")) return "String";
        return "String";
    }
}