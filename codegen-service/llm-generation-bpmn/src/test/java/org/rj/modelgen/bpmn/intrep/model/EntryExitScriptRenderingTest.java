package org.rj.modelgen.bpmn.intrep.model;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.ServiceTask;
import org.camunda.bpm.model.xml.instance.DomDocument;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.junit.jupiter.api.Test;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;
import org.rj.modelgen.bpmn.intrep.model.rendering.ServiceTaskNode;

import java.util.List;

import static org.camunda.bpm.model.bpmn.impl.BpmnModelConstants.ACTIVITI_NS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryExitScriptRenderingTest {

    private static final String NAMESPACE = "http://default.com/example";
    private static final String ENTRY_SCRIPT = "log.info('entering')";
    private static final String EXIT_SCRIPT = "log.info('exiting')";

    private static BpmnModelInstance serviceTaskModel() {
        return Bpmn.createExecutableProcess("process")
                .startEvent("start")
                .serviceTask("task1").name("Task 1")
                .endEvent("end")
                .done();
    }

    private static void attachExecutionListener(BaseElement element, String event, String scriptBody) {
        ExtensionElements extensionElements = element.getExtensionElements();
        if (extensionElements == null) {
            extensionElements = element.getModelInstance().newInstance(ExtensionElements.class);
            element.setExtensionElements(extensionElements);
        }
        DomElement extensionsDom = extensionElements.getDomElement();
        DomDocument doc = extensionsDom.getDocument();

        DomElement listener = doc.createElement(ACTIVITI_NS, "executionListener");
        listener.setAttribute("event", event);

        DomElement script = doc.createElement(ACTIVITI_NS, "script");
        script.setAttribute("scriptFormat", "groovy");
        script.setTextContent(scriptBody);

        listener.appendChild(script);
        extensionsDom.appendChild(listener);
    }

    private static List<DomElement> executionListeners(ServiceTask task) {
        return task.getExtensionElements().getDomElement().getChildElements().stream()
                .filter(e -> "executionListener".equals(e.getLocalName()))
                .toList();
    }

    @Test
    void reverseRender_extractsEntryAndExitScripts() {
        BpmnModelInstance model = serviceTaskModel();
        ServiceTask task = model.getModelElementById("task1");
        attachExecutionListener(task, "start", ENTRY_SCRIPT);
        attachExecutionListener(task, "end", EXIT_SCRIPT);

        ElementNode node = ElementNode.fromFlowNode(task, new BpmnModelAssets(), NAMESPACE,
                BpmnComponentLibrary.defaultLibrary(), BpmnGlobalVariableLibrary.empty());

        assertEquals(ENTRY_SCRIPT, node.findInput("entryScript").map(ElementNodeInput::getValue).orElse(null));
        assertEquals(EXIT_SCRIPT, node.findInput("exitScript").map(ElementNodeInput::getValue).orElse(null));
    }

    @Test
    void reverseRender_noListeners_producesNoEntryExitInputs() {
        BpmnModelInstance model = serviceTaskModel();
        ServiceTask task = model.getModelElementById("task1");

        ElementNode node = ElementNode.fromFlowNode(task, new BpmnModelAssets(), NAMESPACE,
                BpmnComponentLibrary.defaultLibrary(), BpmnGlobalVariableLibrary.empty());

        assertTrue(node.findInput("entryScript").isEmpty());
        assertTrue(node.findInput("exitScript").isEmpty());
    }

    @Test
    void applyEntryExitScripts_writesExecutionListenersUnderActivitiNamespace() {
        BpmnModelInstance model = serviceTaskModel();

        ElementNode node = new ServiceTaskNode("task1", "Task 1");
        node.setInputs(List.of(
                ElementNodeInput.createInputFromAttribute("entryScript", ENTRY_SCRIPT, true),
                ElementNodeInput.createInputFromAttribute("exitScript", EXIT_SCRIPT, true)
        ));

        node.applyEntryExitScripts(model);

        ServiceTask task = model.getModelElementById("task1");
        List<DomElement> listeners = executionListeners(task);

        assertEquals(2, listeners.size());
        for (DomElement listener : listeners) {
            assertEquals(ACTIVITI_NS, listener.getNamespaceURI());
        }

        DomElement startListener = listeners.stream()
                .filter(l -> "start".equals(l.getAttribute("event"))).findFirst().orElseThrow();
        DomElement endListener = listeners.stream()
                .filter(l -> "end".equals(l.getAttribute("event"))).findFirst().orElseThrow();

        DomElement startScript = startListener.getChildElements().get(0);
        DomElement endScript = endListener.getChildElements().get(0);

        assertEquals(ACTIVITI_NS, startScript.getNamespaceURI());
        assertEquals("groovy", startScript.getAttribute("scriptFormat"));
        assertEquals(ENTRY_SCRIPT, startScript.getTextContent());
        assertEquals(EXIT_SCRIPT, endScript.getTextContent());
    }

    @Test
    void applyEntryExitScripts_noInputs_writesNoExecutionListeners() {
        BpmnModelInstance model = serviceTaskModel();

        ElementNode node = new ServiceTaskNode("task1", "Task 1");
        node.setInputs(List.of());

        node.applyEntryExitScripts(model);

        ServiceTask task = model.getModelElementById("task1");
        assertFalse(task.getExtensionElements() != null && !executionListeners(task).isEmpty());
    }
}
