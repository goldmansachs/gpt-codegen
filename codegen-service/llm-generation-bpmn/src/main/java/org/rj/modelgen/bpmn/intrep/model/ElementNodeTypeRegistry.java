package org.rj.modelgen.bpmn.intrep.model;

import org.camunda.bpm.model.bpmn.instance.*;
import org.rj.modelgen.bpmn.intrep.model.rendering.*;
import org.rj.modelgen.bpmn.intrep.model.rendering.boundaryevents.*;
import org.rj.modelgen.bpmn.intrep.model.rendering.catchevents.*;
import org.rj.modelgen.bpmn.intrep.model.rendering.endevents.*;
import org.rj.modelgen.bpmn.intrep.model.rendering.gateways.EventGatewayNode;
import org.rj.modelgen.bpmn.intrep.model.rendering.gateways.ExclusiveGatewayNode;
import org.rj.modelgen.bpmn.intrep.model.rendering.gateways.InclusiveGatewayNode;
import org.rj.modelgen.bpmn.intrep.model.rendering.gateways.ParallelGatewayNode;
import org.rj.modelgen.bpmn.intrep.model.rendering.startevents.*;
import org.rj.modelgen.bpmn.intrep.model.rendering.throwEvents.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.*;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.SubProcessConfigConstants.SUBPROCESS;


public class ElementNodeTypeRegistry {

    private record TypeEntry(Class<? extends ElementNode> nodeClass, Predicate<FlowNode> matcher) {}

    private static final Map<String, TypeEntry> typeMap = new LinkedHashMap<>();

    static {
        // Start Event Types
        register(MESSAGE_START_EVENT, MessageStartEventNode.class, hasEventDefinition(StartEvent.class, MessageEventDefinition.class));
        register(TIMER_START_EVENT, TimerStartEventNode.class, hasEventDefinition(StartEvent.class, TimerEventDefinition.class));
        register(ERROR_START_EVENT, ErrorStartEventNode.class, hasEventDefinition(StartEvent.class, ErrorEventDefinition.class));
        register(CONDITIONAL_START_EVENT, ConditionalStartEventNode.class, hasEventDefinition(StartEvent.class, ConditionalEventDefinition.class));
        register(START_EVENT, StartEventNode.class, bpmnType("startEvent"));

        // End Event types
        register(TERMINATE_END_EVENT, TerminateEndEventNode.class, hasEventDefinition(EndEvent.class, TerminateEventDefinition.class));
        register(ERROR_END_EVENT, ErrorEndEventNode.class, hasEventDefinition(EndEvent.class, ErrorEventDefinition.class));
        register(MESSAGE_END_EVENT, MessageEndEventNode.class, hasEventDefinition(EndEvent.class, MessageEventDefinition.class));
        register(END_EVENT, EndEventNode.class, bpmnType("endEvent"));

        // Catch Event Types
        register(MESSAGE_INTERMEDIATE_CATCH_EVENT, MessageIntermediateCatchEventNode.class, hasEventDefinition(IntermediateCatchEvent.class, MessageEventDefinition.class));
        register(TIMER_INTERMEDIATE_CATCH_EVENT, TimerIntermediateCatchEventNode.class, hasEventDefinition(IntermediateCatchEvent.class, TimerEventDefinition.class));

        // Throw Event Types
        register(MESSAGE_INTERMEDIATE_THROW_EVENT, MessageIntermediateThrowEventNode.class, hasEventDefinition(IntermediateThrowEvent.class, MessageEventDefinition.class));

        // Boundary event types
        register(TIMER_BOUNDARY_EVENT, TimerBoundaryEventNode.class, hasEventDefinition(BoundaryEvent.class, TimerEventDefinition.class));
        register(MESSAGE_BOUNDARY_EVENT, MessageBoundaryEventNode.class, hasEventDefinition(BoundaryEvent.class, MessageEventDefinition.class));
        register(ERROR_BOUNDARY_EVENT, ErrorBoundaryEventNode.class, hasEventDefinition(BoundaryEvent.class, ErrorEventDefinition.class));
        register(CONDITIONAL_BOUNDARY_EVENT, ConditionalBoundaryEventNode.class, hasEventDefinition(BoundaryEvent.class, ConditionalEventDefinition.class));

        // Tasks
        register(TASK_USER_TASK, UserTaskNode.class, bpmnType("userTask"));
        register(TASK_SERVICE_TASK, ServiceTaskNode.class, bpmnType("serviceTask"));
        register(TASK_SCRIPT_TASK, ScriptTaskNode.class, bpmnType("scriptTask"));
        register(TASK_MANUAL_TASK, ManualTaskNode.class, bpmnType("manualTask"));
        register(TASK_RECEIVE_TASK, ReceiveTaskNode.class, bpmnType("receiveTask"));
        register(TASK_SEND_TASK, SendTaskNode.class, bpmnType("sendTask"));
        register(TASK_BUSINESS_RULE_TASK, BusinessRuleTaskNode.class, bpmnType("businessRuleTask"));
        register(TASK_CALL_ACTIVITY_TASK, CallActivityTaskNode.class, bpmnType("callActivity"));

        // Gateways
        register(GATEWAY_EXCLUSIVE, ExclusiveGatewayNode.class, bpmnType("exclusiveGateway"));
        register(GATEWAY_INCLUSIVE, InclusiveGatewayNode.class, bpmnType("inclusiveGateway"));
        register(GATEWAY_PARALLEL, ParallelGatewayNode.class, bpmnType("parallelGateway"));
        register(GATEWAY_EVENT, EventGatewayNode.class, bpmnType("eventBasedGateway"));

        // Process config & Subprocess
        register(PROCESS_CONFIG, ProcessConfigNode.class, fn -> false);  // never matched from FlowNode
        register(SUBPROCESS, SubProcessNode.class, bpmnType("subProcess"));
    }

    public static void registerType(String elementType, Class<? extends ElementNode> nodeClass) {
        register(elementType, nodeClass, bpmnType(elementType));
    }

    public static void register(String elementType, Class<? extends ElementNode> nodeClass, Predicate<FlowNode> matcher) {
        typeMap.put(elementType, new TypeEntry(nodeClass, matcher));
    }

    public static Class<? extends ElementNode> getNodeClass(String elementType) {
        TypeEntry entry = typeMap.get(elementType);
        return entry != null ? entry.nodeClass() : ElementNode.class;
    }

    public static String resolveType(FlowNode flowNode) {
        for (Map.Entry<String, TypeEntry> entry : typeMap.entrySet()) {
            if (entry.getValue().matcher().test(flowNode)) {
                return entry.getKey();
            }
        }
        return flowNode.getElementType().getTypeName();
    }

    private static Predicate<FlowNode> bpmnType(String typeName) {
        return fn -> typeName.equals(fn.getElementType().getTypeName());
    }

    private static Predicate<FlowNode> hasEventDefinition(Class<? extends FlowNode> eventClass, Class<? extends EventDefinition> definitionClass) {
        return fn -> {
            if (!eventClass.isInstance(fn)) return false;
            java.util.Collection<EventDefinition> defs = getEventDefinitions(fn);
            return defs != null && defs.stream().anyMatch(definitionClass::isInstance);
        };
    }

    private static java.util.Collection<EventDefinition> getEventDefinitions(FlowNode flowNode) {
        if (flowNode instanceof EndEvent e) return e.getEventDefinitions();
        if (flowNode instanceof StartEvent e) return e.getEventDefinitions();
        if (flowNode instanceof IntermediateCatchEvent e) return e.getEventDefinitions();
        if (flowNode instanceof IntermediateThrowEvent e) return e.getEventDefinitions();
        if (flowNode instanceof BoundaryEvent e) return e.getEventDefinitions();
        return java.util.Collections.emptyList();
    }
}
