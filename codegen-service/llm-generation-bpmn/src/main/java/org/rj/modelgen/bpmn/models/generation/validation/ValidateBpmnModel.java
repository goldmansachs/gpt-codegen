package org.rj.modelgen.bpmn.models.generation.validation;

import groovy.lang.GroovyShell;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.builder.AstBuilder;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.*;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.common.BpmnComponentVariableType;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.component.common.BpmnComponentInputSourceType;
import org.rj.modelgen.bpmn.intrep.model.*;
import org.rj.modelgen.bpmn.intrep.model.rendering.gateways.ConditionalGateway;
import org.rj.modelgen.bpmn.intrep.model.rendering.gateways.*;
import org.rj.modelgen.llm.validation.beans.IntermediateModelValidationError;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.*;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.ExecutionListenerConstants.EXIT_SCRIPT_INPUT;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.*;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.Patterns.*;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.SUBPROCESS;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.SUBPROCESS_CALL_NODE;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.SUBPROCESS_ID;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.TRIGGERED_BY_EVENT;
import static org.rj.modelgen.bpmn.component.common.BpmnComponentInputSourceType.*;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.Validation.FULL_PROCESS;
import static org.rj.modelgen.bpmn.models.generation.validation.BpmnScriptUtils.*;
import static org.rj.modelgen.llm.util.ValidationUtils.identifyNumberOfRoots;

public class ValidateBpmnModel {

    private static final String COMMA_DELIMITER = ",";
    private static final List<String> NODES_TO_IGNORE = List.of(PROCESS_CONFIG, SUBPROCESS);

    private BpmnIntermediateModel model;
    private BpmnGlobalVariableLibrary globalVariableLibrary;
    private BpmnComponentLibrary componentLibrary;
    private List<IntermediateModelValidationError> invalidMessages;
    private final GroovyShell shell = new GroovyShell();

    public ValidateBpmnModel(BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        this.globalVariableLibrary = globalVariableLibrary;
        this.componentLibrary = componentLibrary;
    }

    public List<IntermediateModelValidationError> validate(BpmnIntermediateModel model, Set<PayloadVariable> startingPayload) {
        return validate(model, startingPayload, false);
    }

    public List<IntermediateModelValidationError> validate(BpmnIntermediateModel model, Set<PayloadVariable> startingPayload, boolean isSubprocessDL) {
        this.model = model;
        invalidMessages = new ArrayList<>();

        if (isSubprocessDL) {
            validateSubModelStructure(model);
        } else {
            validateProcessStructure(model);
        }

        for (ElementNode node : model.getNodes()) {
            validateNodeNames(node);
            validateRequiredInputs(node);
            validateNodeConnections(node);
            validateNodeConnectionsRules(node);
            validateBoundaryEvents(node);
        }
        identifyOrphanedNodes();

        // Code validation requires traversing a valid graph in execution order so execute it after successful graph structure validation
        if (invalidMessages.isEmpty()) {
            Map<String, List<ElementNode>> predecessors = new HashMap<>(); // Key: node ID, Value: list of immediate predecessor nodes
            Map<String, Set<PayloadVariable>> inVars = new HashMap<>(); // Key: node ID, Value: set of entry variables available to the node before it executes
            Map<String, Set<PayloadVariable>> outVars = new HashMap<>(); // Key: node ID, Value: set of exit variables available after the node executes

            for (ElementNode node : model.getNodes()) {
                inVars.put(node.getId(), new HashSet<>());
                outVars.put(node.getId(), new HashSet<>());

                // Compute predecessors
                predecessors.putIfAbsent(node.getId(), new ArrayList<>());
                List<ElementConnection> outgoingConnections = node.getConnectedTo() == null ? new ArrayList<>() : node.getConnectedTo().stream().toList();
                for (ElementConnection connection : outgoingConnections) {
                    model.getNodes().stream()
                            .filter(n -> n.getId().equals(connection.getTargetNode()))
                            .findFirst()
                            .ifPresent(targetNode ->
                                    predecessors.computeIfAbsent(targetNode.getId(), k -> new ArrayList<>()).add(node));
                }

            }
            traverseGraphAndValidateInputs(predecessors, inVars, outVars, startingPayload);
        }

        return invalidMessages;
    }

    private void validateNodeNames(ElementNode node) {
        if (StringUtils.isBlank(node.getName())) {
            invalidMessages.add(new IntermediateModelValidationError("Node name cannot be null or blank", FULL_PROCESS));
        }
        if (StringUtils.isBlank(node.getId())) {
            invalidMessages.add(new IntermediateModelValidationError("Node id cannot be null or blank", FULL_PROCESS));
        } else {
            if (StringUtils.isBlank(node.getElementType())) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Node type cannot be null or blank for node '%s' ", node.getId()), node.getId()));
            }
            var component = componentLibrary.getComponentByName(node.getElementType());
            if (component.isEmpty()) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Component '%s' is not found in library for node %s. Select a valid component.", node.getElementType(), node.getId()), node.getId()));
            }
        }
    }

    private void validateNodeInputValuesAndVariableSourceExist(ElementNode node, ElementNodeInput input, Collection<IntermediateModelValidationError> invalidInputMessages) {
        if (input.hasProperties()) {
            // Validate all properties of object type input
            for (ElementNodeInput property : input.getProperties()) {
                validateNodeInputValuesAndVariableSourceExist(node, property, invalidInputMessages);
            }
        } else {
            // Validate primitive type input
            if (StringUtils.isBlank(input.getName())) {
                invalidInputMessages.add(new IntermediateModelValidationError(String.format("Node '%s' has input with null or empty name", node.getId()), node.getId()));
            } else {
                if (input.getValue() == null) {
                    invalidInputMessages.add(new IntermediateModelValidationError(String.format("Node '%s' has '%s' input with null value", node.getId(), input.getName()), node.getId()));
                }
                if (StringUtils.isBlank(input.getVariableSource())) {
                    invalidInputMessages.add(new IntermediateModelValidationError(String.format("Node '%s' has '%s' input with null or empty variableSource. It should be CONSTANT, EXPRESSION, SCRIPT or GLOBAL.", node.getId(), input.getName()), node.getId()));
                }
            }
        }
    }

    private void validateNodeInputValuesMatchDefinition(ElementNode node, List<ElementNodeInput> inputs, BpmnComponent.InputVariable inputDefinition, String inputPath) {
        final String path = (inputPath == null || inputPath.isBlank()) ? inputDefinition.getName() : inputPath;

        // If the input is mandatory, it must be present
        if (inputs == null || inputs.isEmpty()) {
            if (inputDefinition.isMandatory()) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Node '%s' is missing mandatory input '%s'", node.getId(), path), node.getId()));
            }
            return;
        }
        // Input must not be defined multiple times unless input definition is an array
        if (inputs.size() > 1 && !inputDefinition.getType().equals(BpmnComponentVariableType.Array)) {
            invalidMessages.add(new IntermediateModelValidationError(String.format("Node '%s' has multiple definitions of input '%s'", node.getId(), path), node.getId()));
            return;
        }

        for (ElementNodeInput input : inputs) {
            if (input.hasProperties()) {
                // If input is an object, input definition has to be an object type as well
                if (inputDefinition.getProperties().isEmpty()) {
                    invalidMessages.add(new IntermediateModelValidationError(String.format("Node '%s' has an object type input '%s' but the input definition is a primitive type", node.getId(), path), node.getId()));
                    continue;
                }
                // If input is an object, validate its properties recursively
                for (var inputDefProp : inputDefinition.getProperties()) {
                    final String childPath = path + "." + inputDefProp.getName();
                    var inputProperty = input.getProperties().stream()
                            .filter(x -> x.getName().equals(inputDefProp.getName()))
                            .findFirst();

                    if (inputProperty.isEmpty()) {
                        if (inputDefProp.isMandatory()) {
                            invalidMessages.add(new IntermediateModelValidationError(String.format("Node '%s' is missing mandatory property '%s'", node.getId(), childPath), node.getId()));
                        }
                        continue;
                    }
                    validateNodeInputValuesMatchDefinition(node, List.of(inputProperty.get()), inputDefProp, childPath);
                }
            } else { // Primitive type input
                // If input is primitive type, input definition hast to be primitive type as well
                if (!inputDefinition.getProperties().isEmpty()) {
                    invalidMessages.add(new IntermediateModelValidationError(String.format("Node '%s' has a primitive type input '%s' but the input definition is an object type with properties", node.getId(), path), node.getId()));
                    continue;
                }
                // Input value can be empty only if it's explicitly provided as empty string in the runbook or if its default value is empty string
                if (input.getValue().isEmpty() && !input.getIsProvided() && (inputDefinition.getDefaultValue() == null || !inputDefinition.getDefaultValue().isEmpty())) {
                    invalidMessages.add(new IntermediateModelValidationError(String.format("Node '%s' has an empty value for input '%s'", node.getId(), path), node.getId()));
                }
                // If the input is enum and constant, it must have a valid value
                if (inputDefinition.getAllowedValues() != null && !inputDefinition.getAllowedValues().isEmpty() && input.getVariableSource().equals(CONSTANT.toString()) && !inputDefinition.getAllowedValues().contains(input.getValue())) {
                    invalidMessages.add(new IntermediateModelValidationError(String.format("Node '%s' has an invalid value for input '%s'. Allowed values are: %s", node.getId(), path, String.join(", ", inputDefinition.getAllowedValues())), node.getId()));
                }
                // Input's source type must be among the component allowed source types
                if (!inputDefinition.isAllowedInputSourceType(input.getVariableSource())) {
                    invalidMessages.add(new IntermediateModelValidationError(String.format("Node '%s' has an input '%s' with invalid variable source type '%s'. Allowed source types for this input are: %s",
                            node.getId(), path, input.getVariableSource(), String.join(", ", inputDefinition.getAllowedInputSourceTypes().stream().map(BpmnComponentInputSourceType::toString).toList())), node.getId()));
                }
            }
        }
    }

    private void validateRequiredInputs(ElementNode node) {
        final Collection<IntermediateModelValidationError> invalidInputMessages = new ArrayList<>();
        final String componentName = node.isSubprocessCallNode() ? SUBPROCESS_CALL_NODE : node.getElementType();
        final var component = componentLibrary.getComponentByName(componentName);
        if (component.isEmpty()) return;   // Unknown action type, should probably never happen by the time we reach this point
        if (node.getInputs() == null) return;

        for (ElementNodeInput nodeInput : node.getInputs()) {
            validateNodeInputValuesAndVariableSourceExist(node, nodeInput, invalidInputMessages);
        }

        if (!invalidInputMessages.isEmpty()) {
            // All inputs must have valid values before proceeding with further validation
            invalidMessages.addAll(invalidInputMessages);
            return;
        }

        if(component.get().getRequiredInputs() == null) {
            return;
        }

        for (var inputDefinition : component.get().getRequiredInputs()) {
            List<ElementNodeInput> inputs = node.getInputs() == null
                    ? new ArrayList<>()
                    : node.getInputs().stream()
                      .filter(nodeInput -> nodeInput.getName().equals(inputDefinition.getName()))
                      .toList();

            validateNodeInputValuesMatchDefinition(node, inputs, inputDefinition, inputDefinition.getName());
        }
    }

    private void validateNodeConnections(ElementNode node) {
        if (node.getConnectedTo() != null) {
            node.getConnectedTo().forEach(connection -> {
                if (StringUtils.isBlank(connection.getTargetNode())) {
                    invalidMessages.add(new IntermediateModelValidationError(String.format("Connection for node '%s' has no target node defined", node.getId()), node.getId()));
                } else if (model.getNodes().stream().noneMatch(n -> n.getId().equals(connection.getTargetNode()))) {
                    invalidMessages.add(new IntermediateModelValidationError(String.format("Target node '%s' does not exist in the model, but the connection of node '%s' tries to point to this non-existent node", connection.getTargetNode(), node.getId()), node.getId()));
                }
            });
        }
    }

    private void validateBoundaryEvents(ElementNode node) {
        var events = node.getEvents();
        if (events == null || events.isEmpty()) return;

        var nodeIds = model.getNodes().stream().map(ElementNode::getId).collect(java.util.stream.Collectors.toSet());

        for (var event : events) {
            if (event.getEventType() == null || event.getEventType().isBlank()) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Boundary event '%s' on node '%s' is missing eventType.", event.getId(), node.getId()), node.getId()));
            } else if (!BOUNDARY_EVENT_TYPES.contains(event.getEventType())) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Boundary event '%s' on node '%s' has invalid eventType '%s'.", event.getId(), node.getId(), event.getEventType()), node.getId()));
            }

            if (TIMER_BOUNDARY_EVENT.equals(event.getEventType())) {
                boolean hasValidTimerConfig = event.findInput(TIMER_DURATION).isPresent() || event.findInput(TIMER_DATE).isPresent() || event.findInput(TIMER_CYCLE).isPresent();
                if (!hasValidTimerConfig) {
                    invalidMessages.add(new IntermediateModelValidationError(String.format("Timer boundary event '%s' on node '%s' is missing timer configuration. At least one of '%s', '%s', or '%s' inputs is required.", event.getId(), node.getId(), TIMER_DURATION, TIMER_DATE, TIMER_CYCLE), node.getId()));
                }
            }

            if (MESSAGE_BOUNDARY_EVENT.equals(event.getEventType())) {
                if (event.findInput(MESSAGE_REF).isEmpty()) {
                    invalidMessages.add(new IntermediateModelValidationError(String.format("Message boundary event '%s' on node '%s' is missing required '%s' input. A message boundary event must specify the message it listens for.", event.getId(), node.getId(), MESSAGE_REF), node.getId()));
                }
            }

            if (CONDITIONAL_BOUNDARY_EVENT.equals(event.getEventType())) {
                if (event.findInput(CONDITION_EXPRESSION).isEmpty()) {
                    invalidMessages.add(new IntermediateModelValidationError(String.format("Conditional boundary event '%s' on node '%s' is missing required '%s' input. A conditional boundary event must specify the condition expression to evaluate.", event.getId(), node.getId(), CONDITION_EXPRESSION), node.getId()));
                }
            }

            if (event.getConnectedTo() == null || event.getConnectedTo().isEmpty()) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Boundary event '%s' on node '%s' has no connectedTo targets.", event.getId(), node.getId()), node.getId()));
            } else {
                for (var conn : event.getConnectedTo()) {
                    if (!nodeIds.contains(conn.getTargetNode())) {
                        invalidMessages.add(new IntermediateModelValidationError(String.format("Boundary event '%s' on node '%s' references target '%s' which does not exist in the model.", event.getId(), node.getId(), conn.getTargetNode()), node.getId()));
                    }
                }
            }
        }
    }


    private Set<String> collectBoundaryEventTargets() {
        return model.getNodes().stream()
                .filter(n -> n.getEvents() != null)
                .flatMap(n -> n.getEvents().stream())
                .filter(e -> e.getConnectedTo() != null)
                .flatMap(e -> e.getConnectedTo().stream())
                .map(ElementConnection::getTargetNode)
                .collect(Collectors.toSet());
    }

    private void identifyOrphanedNodes() {
        // Identify number of roots but ignore processConfig node
        List<String> roots = identifyNumberOfRoots(model, node -> !NODES_TO_IGNORE.contains(node.getElementType()));

        // Nodes that are targets of boundary events are not true roots — they have implicit incoming connections
        Set<String> boundaryTargets = collectBoundaryEventTargets();
        roots = roots.stream().filter(r -> !boundaryTargets.contains(r)).toList();

        boolean hasFlowNodes = model.getNodes().stream().anyMatch(node -> !NODES_TO_IGNORE.contains(node.getElementType()));
        if (!hasFlowNodes) {
            return;
        }

        if (roots.isEmpty()) {
            invalidMessages.add(new IntermediateModelValidationError("Model has no roots - it is cyclic and therefore invalid. The process must be a single continuous process with one root.", FULL_PROCESS));
            return;
        }

        if (roots.size() > 1) {
            invalidMessages.add(new IntermediateModelValidationError(String.format("Model has multiple roots: %s. The process must be a single continuous process with one root.", String.join(", ", roots)), FULL_PROCESS));
        }
    }

    private void validateProcessStructure(BpmnIntermediateModel model) {
        boolean hasProcessConfig = model.getNodes().stream()
                .anyMatch(node -> PROCESS_CONFIG.equals(node.getElementType()));
        if (!hasProcessConfig) {
            invalidMessages.add(new IntermediateModelValidationError("The main process is missing a 'processConfig' node. Every process must include a processConfig node that defines the process identity (processId, processName, etc.).", FULL_PROCESS));
        }

        // Main process must have a start event node
        boolean hasStartEvent = model.getNodes().stream()
                .anyMatch(node -> isStartEventType(node.getElementType()));
        if (!hasStartEvent) {
            invalidMessages.add(new IntermediateModelValidationError("The main process is missing a start event node. Every process must include exactly one start event (e.g. startEvent, messageStartEvent, timerStartEvent) as the entry point of the flow.", FULL_PROCESS));
        }

        // Main process must have an end event node
        boolean hasEndEvent = model.getNodes().stream()
                .anyMatch(node -> isEndEventType(node.getElementType()));
        if (!hasEndEvent) {
            invalidMessages.add(new IntermediateModelValidationError("The main process is missing an end event node. Every process must include at least one end event (e.g. endEvent, terminateEndEvent, errorEndEvent, messageEndEvent) as the termination point of the flow.", FULL_PROCESS));
        }

        validateSubProcessIdFormat(model);
    }

    private void validateSubModelStructure(BpmnIntermediateModel model) {
        if (!model.hasSubModels()) return;

        for (BpmnIntermediateModel subModel : model.getSubModels()) {
            SubProcessConfig config = subModel.getSubProcessConfig();
            String subModelLabel = resolveSubModelLabel(config);
            boolean isEventSubProcess = config != null && config.isTriggeredByEvent();

            boolean hasStartEvent = subModel.getNodes().stream().anyMatch(node -> isStartEventType(node.getElementType()));

            if (!hasStartEvent) {
                String subprocessType = isEventSubProcess ? "Event subprocess" : "Inline subprocess";
                invalidMessages.add(new IntermediateModelValidationError(String.format("%s '%s' is missing a start event node. Every subprocess must include a start event as the entry point of its internal flow.", subprocessType, subModelLabel), FULL_PROCESS));
            }

            // Event subprocesses must use an event-triggered start (messageStartEvent, timerStartEvent, etc.), not a plain startEvent
            if (isEventSubProcess && hasStartEvent) {
                validateEventSubProcessStartType(subModel, subModelLabel);
            }

            // Every subprocess must have at least one end event
            boolean hasEndEvent = subModel.getNodes().stream() .anyMatch(node -> isEndEventType(node.getElementType()));
            if (!hasEndEvent) {
                String subprocessType = isEventSubProcess ? "Event subprocess" : "Inline subprocess";
                invalidMessages.add(new IntermediateModelValidationError(String.format("%s '%s' is missing an end event node. Every subprocess must include at least one end event (e.g. endEvent, terminateEndEvent, errorEndEvent, messageEndEvent) as the termination point of its flow.", subprocessType, subModelLabel), FULL_PROCESS));
            }

            // Inline subprocesses must have a subProcessId in SP<number> format for stable matching
            if (!isEventSubProcess && config != null) {
                String spId = config.getSubProcessId();
                if (spId != null && !spId.isBlank() && !isValidSubProcessIdFormat(spId)) {
                    invalidMessages.add(new IntermediateModelValidationError(String.format("Inline subprocess '%s' has subProcessId '%s' which does not match the required format SP<number> (e.g. SP1, SP2). The subProcessId must follow this convention for stable matching between the main process and its subprocesses.", subModelLabel, spId), FULL_PROCESS));
                }
            }

            // Validate individual nodes within the subprocess (connections, names)
            validateSubModelNodes(subModel, subModelLabel);
        }
    }

    private void validateSubModelNodes(BpmnIntermediateModel subModel, String subModelLabel) {
        Set<String> subNodeIds = subModel.getNodes().stream()
                .map(ElementNode::getId)
                .collect(Collectors.toSet());

        for (ElementNode node : subModel.getNodes()) {
            validateNodeNames(node);

            // Validate connections reference nodes that exist within the subprocess
            if (node.getConnectedTo() != null) {
                for (ElementConnection conn : node.getConnectedTo()) {
                    if (conn.getTargetNode() == null || conn.getTargetNode().isBlank()) {
                        invalidMessages.add(new IntermediateModelValidationError(
                                String.format("Node '%s' in subprocess '%s' has a connection with no target node defined",
                                        node.getId(), subModelLabel), node.getId()));
                    } else if (!subNodeIds.contains(conn.getTargetNode())) {
                        invalidMessages.add(new IntermediateModelValidationError(
                                String.format("Node '%s' in subprocess '%s' references target '%s' which does not exist within the subprocess",
                                        node.getId(), subModelLabel, conn.getTargetNode()), node.getId()));
                    }
                }
            }
        }
    }

    private static String resolveSubModelLabel(SubProcessConfig config) {
        if (config == null) return "unknown";
        if (config.getSubProcessName() != null) return config.getSubProcessName();
        if (config.getSubProcessId() != null) return config.getSubProcessId();
        return "unknown";
    }

    private void validateEventSubProcessStartType(BpmnIntermediateModel subModel, String subModelLabel) {
        boolean hasPlainStartOnly = subModel.getNodes().stream()
                .filter(node -> isStartEventType(node.getElementType()))
                .allMatch(node -> START_EVENT.equals(node.getElementType()));
        if (hasPlainStartOnly) {
            invalidMessages.add(new IntermediateModelValidationError(String.format("Event subprocess '%s' uses a plain 'startEvent' but event subprocesses require an event-triggered start event (e.g. messageStartEvent, timerStartEvent, errorStartEvent, conditionalStartEvent).", subModelLabel), FULL_PROCESS));
        }
    }

    private void validateSubProcessIdFormat(BpmnIntermediateModel model) {
        for (ElementNode node : model.getNodes()) {
            final boolean isCallNode = node.isSubprocessCallNode();
            final boolean isDefinitionNode = SUBPROCESS.equals(node.getElementType());
            if (!isCallNode && !isDefinitionNode) continue;

            // Skip event subprocess config nodes
            if (isDefinitionNode) {
                boolean isEventSubProcess = node.findInput(TRIGGERED_BY_EVENT)
                        .map(input -> "true".equalsIgnoreCase(input.getValue()))
                        .orElse(false);
                if (isEventSubProcess) continue;
            }

            String spId = node.findInput(SUBPROCESS_ID).map(ElementNodeInput::getValue).orElse(null);

            String nodeType = isCallNode ? "Inline subprocessCallNode" : "SubProcess config node";
            if (spId == null || spId.isBlank()) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("%s '%s' is missing a 'subProcessId' input. The subProcessId must match the format SP<number> (e.g. SP1, SP2) to enable stable matching between the main process and its subprocesses.", nodeType, node.getId()), node.getId()));
            } else if (!isValidSubProcessIdFormat(spId)) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("%s '%s' has subProcessId '%s' which does not match the required format SP<number> (e.g. SP1, SP2).", nodeType, node.getId(), spId), node.getId()));
            }
        }
    }

    private static boolean isValidSubProcessIdFormat(String id) {
        if (id == null || id.length() < 3) return false;
        String upper = id.toUpperCase();
        if (!upper.startsWith("SP")) return false;
        String numPart = upper.substring(2);
        return !numPart.isEmpty() && numPart.chars().allMatch(Character::isDigit);
    }

    private void validateNodeConnectionsRules(ElementNode node) {
        if (StringUtils.isBlank(node.getElementType())) return;

        var incomingConnections = model.getNodes().stream()
                .filter(n -> n.getConnectedTo() != null)
                .filter(n -> n.getConnectedTo().stream().anyMatch(c -> c.getTargetNode().equals(node.getId())))
                .toList();

        // Also count boundary event connections as incoming
        boolean hasBoundaryEventIncoming = model.getNodes().stream()
                .filter(n -> n.getEvents() != null)
                .flatMap(n -> n.getEvents().stream())
                .filter(e -> e.getConnectedTo() != null)
                .anyMatch(e -> e.getConnectedTo().stream().anyMatch(c -> c.getTargetNode().equals(node.getId())));

        int totalIncoming = incomingConnections.size() + (hasBoundaryEventIncoming ? 1 : 0);
        final Collection<ElementConnection> connectedTo = node.getConnectedTo() != null ? node.getConnectedTo() : List.of();

        if (node.getElementType().equals(START_EVENT)
                || node.getElementType().equals(MESSAGE_START_EVENT)
                || node.getElementType().equals(TIMER_START_EVENT)
                || node.getElementType().equals(ERROR_START_EVENT)
                || node.getElementType().equals(CONDITIONAL_START_EVENT)) {
            if (!incomingConnections.isEmpty()) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Start event node '%s' has incoming connections. A start event can only have outgoing connections.", node.getId()), node.getId()));
            }
            if (connectedTo.isEmpty()) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Start event node '%s' has no outgoing connections. A start event must have at least one outgoing connection.", node.getId()), node.getId()));
            }
        } else if (node.getElementType().equals(END_EVENT)
                || node.getElementType().equals(TERMINATE_END_EVENT)
                || node.getElementType().equals(ERROR_END_EVENT)) {
            if (!connectedTo.isEmpty()) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("End event node '%s' has outgoing connections. An end event can only have incoming connections.", node.getId()), node.getId()));
            }
            if (totalIncoming == 0) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("End event node '%s' has no incoming connections. An end event must have at least one incoming connection.", node.getId()), node.getId()));
            }
        } else if (BOUNDARY_EVENT_TYPES.contains(node.getElementType())) {
            // Standalone boundary event nodes should not exist — they should be in events[] of the parent
            invalidMessages.add(new IntermediateModelValidationError(String.format("Boundary event '%s' found as a standalone node. Boundary events must be embedded in the parent task's events[] array, not as standalone nodes.", node.getId()), node.getId()));
        } else if (node.getElementType().endsWith(GATEWAY_SUFFIX)) {
            // Gateway can either be a split (one incoming, multiple outgoing) or a merge (multiple incoming, one outgoing), but not both at the same time
            if (totalIncoming > 1 && connectedTo.size()  > 1) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Gateway node '%s' has multiple incoming connections (%d) and multiple outgoing connections (%d). A gateway node can either be a split (one incoming, multiple outgoing) or a merge (multiple incoming, one outgoing), but not both at the same time. Create a separate gateway node for split and merge functionality.", node.getId(), totalIncoming, node.getConnectedTo().size()), node.getId()));
            }
            if (node instanceof ConditionalGateway conditionalGateway) {
                String defaultTargetNodeName = conditionalGateway.getDefaultTargetNodeId();
                Map<String, String> conditions = conditionalGateway.getConditions();
                if (conditions == null) conditions = Map.of();

                // Validate that default node and conditions are mapped to existing nodes. Merge gateways with one outgoing connection do not need to have default node or conditions defined
                if (connectedTo.size() > 1) {
                    if (StringUtils.isBlank(defaultTargetNodeName)) {
                        invalidMessages.add(new IntermediateModelValidationError(String.format("Gateway node '%s' is missing a 'default' input specifying the default target node.", node.getId()), node.getId()));
                    } else if (connectedTo.stream().noneMatch(c -> c.getTargetNode().equals(defaultTargetNodeName))) {
                        invalidMessages.add(new IntermediateModelValidationError(String.format("Gateway node '%s' has a default target node '%s' which is not among its outgoing connections. The 'default' input must specify target node from one of the outgoing connections.", node.getId(), defaultTargetNodeName), node.getId()));
                    }
                }

                for (String conditionTargetNode : conditions.keySet()) {
                    if (connectedTo.stream().noneMatch(c -> c.getTargetNode().equals(conditionTargetNode))) {
                        invalidMessages.add(new IntermediateModelValidationError(String.format("Gateway node '%s' has a condition target node '%s' which is not among its outgoing connections. All condition target nodes must be one of the outgoing connections.", node.getId(), conditionTargetNode), node.getId()));
                    }
                }

                // If there are multiple branches, then only one is allowed to have no condition expression (default path)
                List<String> emptyConditionExpressions = conditions.values().stream().filter(String::isEmpty).toList();
                if (conditions.size() > 1 && emptyConditionExpressions.size() > 1) {
                    invalidMessages.add(new IntermediateModelValidationError(String.format("Gateway node '%s' has multiple condition expressions but %d of them are empty. Only one condition expression can be empty and it should match the default path.", node.getId(), emptyConditionExpressions.size()), node.getId()));
                }
            }
        } else if (node.getElementType().equals(PROCESS_CONFIG) || (node.getElementType().equals(SUBPROCESS) && !node.isSubprocessCallNode())) {
            // processConfig and subProcess definition nodes must be orphan nodes with no connections.
            // Inline subProcess call nodes (those with connections) are NOT definition nodes and are exempt.
            if (!incomingConnections.isEmpty()) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Configuration node '%s' has incoming connections. A configuration node must be an orphan node.", node.getId()), node.getId()));
            }
            if (!connectedTo.isEmpty()) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Configuration node '%s' has outgoing connections. A configuration node must be an orphan node.", node.getId()), node.getId()));
            }
        }
        else {
            if (totalIncoming != 1 && connectedTo.size() != 1) {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Node '%s' of type '%s' has %d incoming connections and %d outgoing connections. This type of node must have exactly one incoming and one outgoing connection.", node.getId(), node.getElementType(), totalIncoming, node.getConnectedTo().size()), node.getId()));
            }
        }
    }

    private void validateConstantInput(ElementNode node, ElementNodeInput input, String inputPath) {
        String constantValue = input.getValue().strip();

        // Do not allow variable writes in constant inputs
        List<PayloadVariable> writtenVariables = retrieveWriteVariables(constantValue);
        if (!writtenVariables.isEmpty()) {
            invalidMessages.add(new IntermediateModelValidationError(String.format("Input '%s' in node '%s' is a CONSTANT input but its value stores variables [%s] using setVariable(). CONSTANT inputs cannot write variables. Change its type to SCRIPT (if the input definition allows this type) or provide a valid CONSTANT value.",
                    inputPath, node.getId(), String.join(", ", writtenVariables.stream().map(PayloadVariable::getName).toList())), node.getId()));
        }

        // Do not allow variable reads in constant inputs
        List<PayloadVariable> readVariables = retrieveReadVariables(constantValue);
        if (!readVariables.isEmpty()) {
            invalidMessages.add(new IntermediateModelValidationError(String.format("Input '%s' in node '%s' is a CONSTANT input but its value reads variables [%s] using getVariable(). CONSTANT inputs cannot read variables. Change its type to EXPRESSION or SCRIPT (if the input definition allows this type) or provide a valid CONSTANT value.",
                    inputPath, node.getId(), String.join(", ", readVariables.stream().map(PayloadVariable::getName).toList())), node.getId()));
        }

        // Do not allow global variable reads in constant inputs
        List<PayloadVariable> readGlobalVariables = retrieveGlobalVariables(constantValue, globalVariableLibrary);
        if (!readGlobalVariables.isEmpty()) {
            invalidMessages.add(new IntermediateModelValidationError(String.format("Input '%s' in node '%s' is a CONSTANT input but its value reads global variables [%s] using getGlobalVariable(). CONSTANT inputs cannot read global variables. Change its type to EXPRESSION or SCRIPT (if the input definition allows this type) or provide a valid CONSTANT value.",
                    inputPath, node.getId(), String.join(", ", readGlobalVariables.stream().map(PayloadVariable::getName).toList())), node.getId()));
        }
    }

    private void validateExpressionInput(ElementNode node, ElementNodeInput input, String inputPath, Set<PayloadVariable> startingPayload, Set<PayloadVariable> nodePayload) {
        String expression = input.getValue().strip();

        // Do not allow variable writes in constant inputs
        List<PayloadVariable> writtenVariables = retrieveWriteVariables(expression);
        if (!writtenVariables.isEmpty()) {
            invalidMessages.add(new IntermediateModelValidationError(String.format("Input '%s' in node '%s' is an EXPRESSION input but its value stores variables [%s] using setVariable(). EXPRESSION inputs cannot write variables. Change its type to SCRIPT (if the input definition allows this type) or provide a valid CONSTANT value.",
                    inputPath, node.getId(), String.join(", ", writtenVariables.stream().map(PayloadVariable::getName).toList())), node.getId()));
        }

        validateVariableReads(node, input, inputPath, startingPayload, nodePayload);
        expression = resolveVariableReadsAsPayloadVar(expression, true);

        validateGlobalVariableReads(node, input, inputPath);
        expression = resolveGlobalVariableReads(expression, globalVariableLibrary, true);
        validateExpression(node, inputPath, expression);
    }

    private void validateExpression(ElementNode node, String inputPath, String expression) {
        if (expression.startsWith("return ")) {
            expression = expression.substring(7).trim();
        }
        if (expression.startsWith("/")) {
            expression = expression.substring(1);
        }
        // If the expression contains interpolation syntax or it is a json object, wrap it with additional quotes to parse it as a GString or PropertyExpression
        if (!expression.startsWith("\"") && !expression.endsWith("\"") && (expression.contains("${") || expression.startsWith("{") && expression.endsWith("}"))) {
            // Escape any '$' characters that are not part of '${' interpolation to avoid Groovy GString parse errors
            expression = expression.replaceAll("\\$(?!\\{)", "\\\\\\$");
            expression = "\"" + expression + "\"";
        }

        try {
            AstBuilder astBuilder = new AstBuilder();
            List<ASTNode> astNodes = astBuilder.buildFromString(CompilePhase.CONVERSION, expression);

            var astNode = astNodes.get(0);
            List<Statement> statements = ((BlockStatement) astNode).getStatements();

            if (statements.size() == 1) {
                Statement statement = statements.get(0);
                if (statement instanceof ExpressionStatement) {
                    Expression expr = ((ExpressionStatement) statement).getExpression();
                    if (!isValidExpressionType(expr)) {
                        invalidMessages.add(new IntermediateModelValidationError(String.format("Input '%s' in node '%s' is an EXPRESSION input but its value is a '%s' expression which is not allowed. EXPRESSION inputs must have single-line expressions. Change its source type to CONSTANT or SCRIPT, or provide a valid EXPRESSION value.",
                                inputPath, node.getId(), expr.getClass().getSimpleName()), node.getId()));
                    }
                }
            } else {
                invalidMessages.add(new IntermediateModelValidationError(String.format("Input '%s' in node '%s' is an EXPRESSION input but its value appears to be a Groovy script. EXPRESSION inputs must have single-line expressions. Change its source type to SCRIPT or provide a valid EXPRESSION value.",
                        inputPath, node.getId()), node.getId()));
            }
        } catch (Exception e) {
            invalidMessages.add(new IntermediateModelValidationError(String.format("Input '%s' in node '%s' is an EXPRESSION input but its value could not be parsed as a valid Groovy expression and threw an error: %s. EXPRESSION inputs must have single-line expressions. Change its source type to SCRIPT or provide a valid EXPRESSION value.",
                    inputPath, node.getId(), e.getMessage()), node.getId()));
        }
    }

    private boolean isValidExpressionType(Expression expr) {
        return expr instanceof BinaryExpression ||
                expr instanceof VariableExpression ||
                expr instanceof GStringExpression ||
                expr instanceof PropertyExpression ||
                expr instanceof CastExpression ||
                expr instanceof ConstantExpression;
    }

    private void validateScriptInput(ElementNode node, ElementNodeInput input, String inputPath, Set<PayloadVariable> startingPayload, Set<PayloadVariable> nodePayload) {
        String script = input.getValue();

        script = replaceNewLines(script);

        // Check for variable writes
        List<PayloadVariable> writtenVariables = retrieveWriteVariables(script);
        script = resolveVariableWrites(script);

        // Check for variable reads
        boolean isScriptValid = validateVariableReads(node, input, inputPath, startingPayload, nodePayload) && validateGlobalVariableReads(node, input, inputPath);
        script = resolveVariableReadsAsPayloadVar(script, false);
        script = resolveGlobalVariableReads(script, globalVariableLibrary, false);

        // Replace throw(...) but don't use valid Groovy exception syntax as it would throw actual exception during evaluation
        script = resolveErrorThrows(script, "errorMessagePlaceholder = $1");

        // Groovy script validation will fail if there are variable read errors so only proceed if there are no such errors
        if (isScriptValid) {
            String globalVarInitScript = buildGlobalVarsInitScript();
            script = globalVarInitScript + buildDummyPayload(input, startingPayload, nodePayload) + script;

            if (parseGroovyScript(node, inputPath, script)) {
                validateGroovyScript(node, inputPath, script);
            }
        }
    }

    private boolean validateVariableReads(ElementNode node, ElementNodeInput input, String inputPath, Set<PayloadVariable> startingPayload, Set<PayloadVariable> nodePayload) {
        boolean isInputValid = true;
        Matcher readMatcher = VAR_READ_PATTERN.matcher(input.getValue());
        while (readMatcher.find()) {
            String variableName = readMatcher.group(1);

            // Validate if the current node has access to the variable
            boolean foundInPayload = Stream.ofNullable(nodePayload).flatMap(Collection::stream)
                    .anyMatch(payloadVar -> payloadVar.getName().equals(variableName) || variableName.startsWith(payloadVar.getName() + "."));

            // Fallback: check if variable exists in the starting payload
            boolean foundInStartingPayload = Stream.ofNullable(startingPayload).flatMap(Collection::stream)
                    .anyMatch(payloadVar -> payloadVar.getName().equals(variableName) || variableName.startsWith(payloadVar.getName() + "."));

            if (!foundInPayload && !foundInStartingPayload) {
                invalidMessages.add(new IntermediateModelValidationError(String.format(
                        "Input '%s' in node '%s' reads variable '%s' which is not available at this point in the process. " +
                                "Ensure this variable is provided in the starting payload or written in any of the subsequent nodes scripts using setVariable().",
                        inputPath, node.getId(), variableName), node.getId()));
                isInputValid = false;
            }
        }
        return isInputValid;
    }

    private boolean validateGlobalVariableReads(ElementNode node, ElementNodeInput input, String inputPath) {
        boolean isScriptValid = true;
        Matcher globalVarMatcher = GLOBAL_VAR_READ_PATTERN.matcher(input.getValue());
        while (globalVarMatcher.find()) {
            String variableName = globalVarMatcher.group(1);
            String arguments = globalVarMatcher.group(2);

            // Check if global variable exists in the global variable library
            var globalVar = globalVariableLibrary.getVariableByName(variableName);
            if (globalVar.isEmpty()) {
                invalidMessages.add(new IntermediateModelValidationError(
                        String.format("%s in node '%s' reads global variable '%s' which does not exist in the global variable library. " +
                                        "Change this input to a global variable which is in the library, or to another input type such as CONSTANT, EXPRESSION or SCRIPT if appropriate.",
                                inputPath, node.getId(), variableName), node.getId()));
                isScriptValid = false;
            } else {
                // Verify that all required arguments are provided
                var requiredArgs = globalVar.get().getArguments();
                if (StringUtils.isNotBlank(arguments) && arguments.trim().split(COMMA_DELIMITER).length != requiredArgs.size()) {
                    invalidMessages.add(new IntermediateModelValidationError(
                            String.format("%s in node '%s' reads global variable '%s' but the number of provided arguments (%d) does not match the required number of arguments (%d). " +
                                            "Ensure to provide all required arguments when reading this global variable.",
                                    inputPath, node.getId(), variableName, arguments.trim().split(COMMA_DELIMITER).length, requiredArgs.size()), node.getId()));
                    isScriptValid = false;
                }
            }
        }
        return isScriptValid;
    }

    private String buildDummyPayload(ElementNodeInput input, Set<PayloadVariable> startingPayload, Set<PayloadVariable> nodePayload) {
        StringBuilder fullScript = new StringBuilder("def payload = [:];\n");
        // Accumulate variables from the starting payload with current payload
        // Sort by depth to ensure parent objects are created before child properties
        List<PayloadVariable> sortedPayloadVariables = Stream.concat(
                        Stream.ofNullable(startingPayload).flatMap(Collection::stream),
                        Stream.ofNullable(nodePayload).flatMap(Collection::stream))
                .sorted(Comparator.comparingInt(var -> var.getName().split("\\.").length))
                .toList();

        Set<String> initializedVars = new HashSet<>();

        for (PayloadVariable variable : sortedPayloadVariables) {
            String[] parts = variable.getName().split("\\.");
            StringBuilder currentGroovyVar = new StringBuilder();

            for (int i = 0; i < parts.length; i++) {
                currentGroovyVar.append(i > 0 ? "." : "").append(parts[i]);
                String fullIntermediateVar = currentGroovyVar.toString();

                if (!initializedVars.contains(fullIntermediateVar)) {
                    boolean hasChildren = i < parts.length - 1 || sortedPayloadVariables.stream().anyMatch(var -> var.getName().startsWith(fullIntermediateVar + "."));

                    if (hasChildren) {
                        fullScript.append("payload.").append(fullIntermediateVar).append(" = payload.").append(fullIntermediateVar).append(" ?: [:];\n");
                    } else {
                        String dummyValue = switch (variable.getType().toLowerCase()) {
                            case "integer" -> "123";
                            case "boolean" -> "true";
                            case "float" -> "123.45";
                            case "array" -> "[]";
                            case "object" -> "[:]";
                            default -> "'default_dummy_value_for_" + variable.getName() + "'";
                        };

                        fullScript.append("payload.").append(fullIntermediateVar).append(" = ").append(dummyValue).append(";\n");
                    }
                    initializedVars.add(fullIntermediateVar);
                }
            }
        }

        // Output script can use "response" output variable so ensure it's initialized
        if (input.getName().equals("outputScript") && !initializedVars.contains("response")) {
            fullScript.append("def response = [:];\n");
            fullScript.append("response.statusLine = [:]\nresponse.statusLine.statusCode = 200\n");
            fullScript.append("response.request = [:]\nresponse.request.uri = 'http://example.com/main/api'\n");
            fullScript.append("response.entity = [:]\n");
        }
        fullScript.append(buildAdditionalDummyPayload(input, startingPayload, nodePayload, initializedVars));

        return fullScript.append("\n").toString();
    }

    // No-op method to allow extending classes to add additional dummy payloads
    protected String buildAdditionalDummyPayload(ElementNodeInput input, Set<PayloadVariable> startingPayload, Set<PayloadVariable> nodePayload, Set<String> initializedVars) {
        return "";
    }

    private String buildGlobalVarsInitScript() {
        StringBuilder script = new StringBuilder();
        Set<String> processedLines = new HashSet<>();

        for (var globalVar : globalVariableLibrary.getComponents()) {
            String initScript = globalVar.getInitScript();
            if (initScript != null && !initScript.isBlank()) {
                String[] lines = initScript.split("\n");
                for (String line : lines) {
                    if (!processedLines.contains(line)) {
                        script.append(line).append("\n");
                        processedLines.add(line);
                    }
                }
            }
        }
        return script.toString();
    }

    private boolean parseGroovyScript(ElementNode node, String inputPath, String script) {
        try {
            shell.parse(script);
            return true;
        } catch (Exception e) {
            invalidMessages.add(new IntermediateModelValidationError(String.format("%s code for node '%s' is not a valid Groovy code or expression. Exception: '%s'", inputPath, node.getId(), e), node.getId()));
            return false;
        }
    }

    private void validateGroovyScript(ElementNode node, String inputPath, String script) {
        try {
            shell.evaluate(script);
        } catch (Exception e) {
            invalidMessages.add(new IntermediateModelValidationError(String.format("Input '%s' in node '%s' is a SCRIPT input but its value could not be evaluated. " +
                            "SCRIPT inputs must contain executable code that may read or write variables using getVariable() and setVariable(). Exception: '%s'",
                    inputPath, node.getId(), e.getMessage()), node.getId()));
        }
    }

    private static final Pattern STRING_OR_COMMENT_OR_ESCAPED_NEWLINE = Pattern.compile(
              "'''(?:\\\\.|(?!''').)*'''"            // triple-single
            + "|\"\"\"(?:\\\\.|(?!\"\"\").)*\"\"\""       // triple-double
            + "|'(?:\\\\.|[^'\\\\])*'"                    // single-quoted
            + "|\"(?:\\\\.|[^\"\\\\])*\""                 // double-quoted
            + "|//[^\\n]*"                                // line comment
            + "|/\\*.*?\\*/"                              // block comment
            + "|\\\\n",                                   // replacement target
            Pattern.DOTALL);

    static String replaceNewLines(String src) {
        if (src == null || src.isEmpty()) return src;

        final Matcher matcher = STRING_OR_COMMENT_OR_ESCAPED_NEWLINE.matcher(src);
        final StringBuilder out = new StringBuilder(src.length());
        while (matcher.find()) {
            final String group = matcher.group();
            if ("\\n".equals(group)) {
                matcher.appendReplacement(out, Matcher.quoteReplacement("\n"));
            } else {
                // A string literal or comment - keep exactly as-is.
                matcher.appendReplacement(out, Matcher.quoteReplacement(group));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    // Data flow analysis
    private void traverseGraphAndValidateInputs(Map<String, List<ElementNode>> predecessors,
                                                Map<String, Set<PayloadVariable>> inVars,
                                                Map<String, Set<PayloadVariable>> outVars,
                                                Set<PayloadVariable> startingPayload) {

        List<String> roots = identifyNumberOfRoots(model, node -> !NODES_TO_IGNORE.contains(node.getElementType())); // Ignore processConfig node

        if (roots.size() == 1) {
            model.getNodeById(roots.get(0)).ifPresent(startNode -> {
                inVars.put(startNode.getId(), startingPayload);
            });
        }

        // Track which variables are automatically generated outputs per node type
        Map<String, Set<PayloadVariable>> generatedOutputsByElementType = new HashMap<>();
        for (BpmnComponent component : componentLibrary.getComponents()) {
            var outputs = getGeneratedOutputsByElementType(component.getName())
                    .stream()
                    .map(outVar -> new PayloadVariable(outVar.getName(), outVar.getType().toString()))
                    .collect(Collectors.toSet());
            generatedOutputsByElementType.put(component.getName(), outputs);
        }

        boolean changed;
        int maxIterations = 1_000_000;
        int iter = 0;
        do {
            changed = false;
            for (ElementNode node : model.getNodes()) {
                // IN[node] = union of OUT[predecessor] and autoGenOut[predecessor] for all predecessors
                Set<PayloadVariable> newInVars = new HashSet<>();
                // OUT[node] = union of OUT[predecessor] for all predecessors + variables written by this node
                Set<PayloadVariable> newOutVars = new HashSet<>();
                for (ElementNode pred : predecessors.getOrDefault(node.getId(), List.of())) {
                    newInVars.addAll(outVars.get(pred.getId()));
                    // Add generated outputs only from immediate predecessors and not propagate to successors down the line, as they are only available to immediate successor nodes
                    newInVars.addAll(generatedOutputsByElementType.getOrDefault(pred.getElementType(), Set.of()));

                    newOutVars.addAll(outVars.get(pred.getId()));
                }

                // Extract variables written by this node's inputs
                if (node.getInputs() != null) {
                    for (ElementNodeInput input : node.getInputs()) {
                        extractWrittenVariables(input, newOutVars);
                    }
                }

                // Check if anything changed
                if (!newInVars.equals(inVars.get(node.getId())) || !newOutVars.equals(outVars.get(node.getId()))) {
                    inVars.put(node.getId(), newInVars);
                    outVars.put(node.getId(), newOutVars);
                    changed = true;
                }
            }
            iter++;
        } while (changed && iter < maxIterations);

        if (changed) {
            throw new RuntimeException("Bpmn model validation failed: Data flow analysis did not converge within the maximum number of iterations.");
        }

        for (ElementNode node : model.getNodes()) {
            if (node.getInputs() != null) {
                var nodePayload = inVars.get(node.getId());

                // exitScript runs after this node's own work completes (e.g. after a serviceTask's outputScript
                // has stored a value via setVariable(), or after a userTask's completion action), so - unlike
                // every other input on this node, including entryScript - it also has access to this same
                // node's own generated outputs (e.g. reasonCode) and anything its OTHER scripts wrote (entryScript,
                // inputScript, outputScript). exitScript's own writes are deliberately excluded here: including
                // them would let a read-before-write within exitScript itself pass validation even though nothing
                // would actually have set that variable yet at runtime.
                Set<PayloadVariable> otherInputWrites = new HashSet<>();
                for (ElementNodeInput otherInput : node.getInputs()) {
                    if (!EXIT_SCRIPT_INPUT.equals(otherInput.getName())) {
                        extractWrittenVariables(otherInput, otherInputWrites);
                    }
                }
                var nodePayloadWithOwnOutputs = new HashSet<>(Stream.ofNullable(nodePayload).flatMap(Collection::stream).toList());
                nodePayloadWithOwnOutputs.addAll(otherInputWrites);
                nodePayloadWithOwnOutputs.addAll(generatedOutputsByElementType.getOrDefault(node.getElementType(), Set.of()));

                String inputPath = "";
                for (ElementNodeInput input : node.getInputs()) {
                    boolean isExitScript = EXIT_SCRIPT_INPUT.equals(input.getName());
                    validateInput(node, input, inputPath, startingPayload, isExitScript ? nodePayloadWithOwnOutputs : nodePayload);
                }
            }
        }
    }

    private void extractWrittenVariables(ElementNodeInput input, Collection<PayloadVariable> writtenVariables) {
        if (input.hasProperties()) {
            for (ElementNodeInput property : input.getProperties()) {
                extractWrittenVariables(property, writtenVariables);
            }
        } else {
            if (input.getVariableSource().equals(SCRIPT.toString())) {
                writtenVariables.addAll(retrieveWriteVariables(input.getValue()));
            }
        }
    }

    private void validateInput(ElementNode node, ElementNodeInput input, String path, Set<PayloadVariable> startingPayload, Set<PayloadVariable> nodePayload) {
        String childPath = path.isEmpty() ? input.getName() : path + "." + input.getName();
        if (input.hasProperties()) {
            for (ElementNodeInput property : input.getProperties()) {
                validateInput(node, property, childPath, startingPayload, nodePayload);
            }
        } else {
            validateInputValue(node, input, childPath, startingPayload, nodePayload);
        }
    }

    private void validateInputValue(ElementNode node, ElementNodeInput input, String path, Set<PayloadVariable> startingPayload, Set<PayloadVariable> nodePayload) {
        if (input.getVariableSource().equals(CONSTANT.toString())) {
            validateConstantInput(node, input, path);
        } else if (input.getVariableSource().equals(EXPRESSION.toString())) {
            validateExpressionInput(node, input, path, startingPayload, nodePayload);
        } else if (input.getVariableSource().equals(SCRIPT.toString())) {
            validateScriptInput(node, input, path, startingPayload, nodePayload);
        }
    }

    private Set<BpmnComponent.Variable> getGeneratedOutputsByElementType(String elementType) {
        var autoGeneratedOutputs = componentLibrary.getAutoGeneratedOutputs();
        if (autoGeneratedOutputs == null) return new HashSet<>();
        return new HashSet<>(Optional.ofNullable(autoGeneratedOutputs.get(elementType)).orElseGet(Set::of));
    }
}
