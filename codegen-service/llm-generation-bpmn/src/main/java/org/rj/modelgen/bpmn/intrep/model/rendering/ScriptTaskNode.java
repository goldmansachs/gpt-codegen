package org.rj.modelgen.bpmn.intrep.model.rendering;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.builder.ScriptTaskBuilder;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Script;
import org.camunda.bpm.model.bpmn.instance.ScriptTask;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.ElementNode;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;
import org.rj.modelgen.bpmn.intrep.model.assets.BpmnModelAssets;

import java.util.ArrayList;
import java.util.List;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.TASK_SCRIPT_TASK;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.ScriptTaskConstants.*;

public class ScriptTaskNode extends ElementNode {
    public ScriptTaskNode() {
        super();
    }

    public ScriptTaskNode(String id, String name) {
        super(id, name, TASK_SCRIPT_TASK);
    }

    @JsonIgnore
    @Override
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        ScriptTaskBuilder taskBuilder = builder.scriptTask(id).name(name);
        ScriptTask task = taskBuilder.getElement();
        configureTaskMetadata(task, namespace);

        String scriptContent = findInput(SCRIPT)
                .map(ElementNodeInput::getValue)
                .orElse("");

        task.setScriptFormat(SCRIPT_FORMAT_GROOVY);
        BpmnModelInstance modelInstance = (BpmnModelInstance) task.getModelInstance();
        Script scriptElement = modelInstance.newInstance(Script.class);
        scriptElement.setTextContent(scriptContent);
        task.setScript(scriptElement);

        return taskBuilder.done();
    }

    @JsonIgnore
    @Override
    protected List<ElementNodeInput> reverseRenderValues(DomElement dom, FlowNode flowNode, String namespace,BpmnComponent.InputVariable iv) {
        if (SCRIPT.equals(iv.getName()) && flowNode instanceof ScriptTask scriptTask) {
            List<ElementNodeInput> result = new ArrayList<>();
            Script script = scriptTask.getScript();
            if (script != null) {
                result.add(ElementNodeInput.createScript(SCRIPT, script.getTextContent()));
            }
            return result;
        }
        return super.reverseRenderValues(dom, flowNode, namespace, iv);
    }
}
