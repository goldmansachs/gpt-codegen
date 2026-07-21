package org.rj.modelgen.bpmn.component.synthetic.types;

import org.rj.modelgen.bpmn.component.synthetic.BpmnSyntheticElementNode;
import org.rj.modelgen.bpmn.intrep.model.BpmnIntermediateModel;
import org.rj.modelgen.bpmn.intrep.model.ElementConnection;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;
import org.rj.modelgen.bpmn.intrep.model.SubProcessConfig;
import org.rj.modelgen.bpmn.intrep.model.rendering.startevents.MessageStartEventNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.IS_INTERRUPTING;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.EventConstants.MESSAGE_REF;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.*;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.ProcessConfigConstants.WORKFLOW_ACTION_DETAILS;

public class BpmnSyntheticTerminateWorkflowNode implements BpmnSyntheticElementNode {

    private static final Logger LOG = LoggerFactory.getLogger(BpmnSyntheticTerminateWorkflowNode.class);

    private static final String TERMINATE_SUBPROCESS_NAME = "Terminate Process";
    private static final String TERMINATE_SUBPROCESS_ID = "TerminateProcess";
    private static final String START_EVENT_ID = "Event_terminate_start";
    private static final String START_EVENT_NAME = "Terminate Workflow";
    private static final String END_EVENT_ID = "Event_terminate_end";
    private static final String DEFAULT_MESSAGE_ID = "terminate";

    // Workflow action constants
    private static final String ACTION_NAME = "SEND_MESSAGE_terminate";
    private static final String ACTION_TYPE = "SEND_MESSAGE";
    private static final String ACTION_DISPLAY_NAME = "Terminate Process";
    private static final String ACTION_AVAILABILITY = "return input.assignedToInvokingUser && input.taskStatus == \"IN_PROGRESS\";";
    private static final String ACTION_MULTIPLICITY = "ANY";
    private static final String ACTION_THEME = "DESTRUCTIVE";

    public BpmnSyntheticTerminateWorkflowNode() {
    }

    @Override
    public void resolve(BpmnIntermediateModel model, ElementNode node) {
        // Resolve is a no-op for the placeholder node; injection is handled via addTerminateWorkflowSubprocess
    }

    @Override
    public void unresolve(BpmnIntermediateModel model, ElementNode node) {
        // No-op: the event subprocess sub-model is removed as a whole
    }

    public static void addTerminateWorkflowSubprocess(BpmnIntermediateModel model) {
        boolean alreadyExists = model.getSubModels().stream()
                .anyMatch(BpmnSyntheticTerminateWorkflowNode::isTerminateWorkflowSubprocess);
        if (alreadyExists) {
            LOG.debug("Terminate workflow event subprocess already present, skipping injection");
            return;
        }

        BpmnIntermediateModel subModel = buildTerminateWorkflowSubModel();
        model.getSubModels().add(subModel);
        LOG.info("Injected terminate workflow event subprocess '{}'", TERMINATE_SUBPROCESS_ID);

        injectTerminateWorkflowAction(model);
    }

    private static boolean isTerminateWorkflowSubprocess(BpmnIntermediateModel subModel) {
        if (subModel == null || subModel.getNodes() == null) return false;
        if (!subModel.isTriggeredByEvent()) return false;

        // Match by subProcessConfig ID
        SubProcessConfig config = subModel.getSubProcessConfig();
        if (config != null && TERMINATE_SUBPROCESS_ID.equals(config.getSubProcessId())) {
            return true;
        }

        // Match by message start event with the terminate message ref
        return subModel.getNodes().stream()
                .filter(node -> MESSAGE_START_EVENT.equals(node.getElementType()))
                .anyMatch(node -> node.findInput(MESSAGE_REF)
                        .map(input -> input.getValue() != null && input.getValue().contains(DEFAULT_MESSAGE_ID))
                        .orElse(false));
    }

    private static BpmnIntermediateModel buildTerminateWorkflowSubModel() {
        BpmnIntermediateModel subModel = new BpmnIntermediateModel();

        // Set subprocess config via SubProcessConfig
        subModel.setSubProcessConfig(new SubProcessConfig(TERMINATE_SUBPROCESS_ID, TERMINATE_SUBPROCESS_NAME, true));

        List<ElementNode> nodes = new ArrayList<>();

        // 1. Interrupting message start event
        ElementNode startEvent = new MessageStartEventNode(START_EVENT_ID, START_EVENT_NAME);
        startEvent.setInputs(List.of(
                createInput(IS_INTERRUPTING, "true"),
                createInput(MESSAGE_REF, DEFAULT_MESSAGE_ID)
        ));
        startEvent.setConnectedTo(List.of(new ElementConnection(END_EVENT_ID, null)));
        nodes.add(startEvent);

        // 2. Plain end event
        ElementNode endEvent = new ElementNode(END_EVENT_ID, null, END_EVENT);
        endEvent.setConnectedTo(List.of());
        nodes.add(endEvent);

        subModel.setNodes(nodes);
        return subModel;
    }

    // Injects a SEND_MESSAGE workflow action into the ProcessConfigNode
    private static void injectTerminateWorkflowAction(BpmnIntermediateModel model) {
        ElementNode processConfig = model.getNodes().stream()
                .filter(node -> PROCESS_CONFIG.equalsIgnoreCase(node.getElementType()))
                .findFirst()
                .orElse(null);
        if (processConfig == null) {
            LOG.debug("No processConfig node found, skipping workflow action injection");
            return;
        }

        // Check if a SEND_MESSAGE action for the terminate message already exists
        boolean exists = processConfig.findAllInputs(WORKFLOW_ACTION_DETAILS).stream()
                .anyMatch(input -> ACTION_TYPE.equals(input.findPropertyValueOrDefault("action", ""))
                        && DEFAULT_MESSAGE_ID.equals(input.findPropertyValueOrDefault(MESSAGE_REF, "")));
        if (exists) {
            LOG.debug("Terminate SEND_MESSAGE workflow action already present, skipping injection");
            return;
        }

        ElementNodeInput actionInput = new ElementNodeInput();
        actionInput.setName(WORKFLOW_ACTION_DETAILS);
        actionInput.setProperties(List.of(
                createInput("name", ACTION_NAME),
                createInput("action", ACTION_TYPE),
                createInput("displayName", ACTION_DISPLAY_NAME),
                createInput("availability", ACTION_AVAILABILITY),
                createInput("messageRef", DEFAULT_MESSAGE_ID),
                createInput("actionMultiplicity", ACTION_MULTIPLICITY),
                createInput("theme", ACTION_THEME)
        ));

        if (processConfig.getInputs() == null) {
            processConfig.setInputs(new ArrayList<>());
        }
        processConfig.getInputs().add(actionInput);
        LOG.info("Injected terminate SEND_MESSAGE workflow action into processConfig");
    }

    private static ElementNodeInput createInput(String name, String value) {
        ElementNodeInput input = new ElementNodeInput();
        input.setName(name);
        input.setValue(value);
        return input;
    }
}
