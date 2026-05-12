package org.rj.modelgen.bpmn.intrep.model;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.builder.AbstractFlowNodeBuilder;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.intrep.model.rendering.*;
import org.rj.modelgen.llm.intrep.graph.GraphNode;
import java.util.*;
import java.util.logging.Logger;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.CommonTaskConstants.*;
import static org.rj.modelgen.bpmn.generation.BpmnConstants.NodeTypes.PROCESS_CONFIG;
import static org.rj.modelgen.bpmn.intrep.model.ElementNodeInput.createInputFromAttribute;
import static org.rj.modelgen.bpmn.intrep.model.common.ElementNodeSharedUtils.extractAttributeValue;
import static org.rj.modelgen.bpmn.intrep.model.common.ElementNodeSharedUtils.getLookupName;
import static org.rj.modelgen.bpmn.models.generation.validation.BpmnScriptUtils.applyFormatValueToAllInputs;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.CUSTOM,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "elementType",
        visible = true)
@JsonTypeIdResolver(ElementNodeTypeIdResolver.class)
public class ElementNode implements GraphNode<String, String, ElementConnection> {
    private static final Logger LOG = Logger.getLogger(ElementNode.class.getName());

    protected String id;
    protected String name;
    protected String elementType;
    protected String description;
    protected List<ElementConnection> connectedTo;
    protected Map<String, Object> properties;
    protected List<ElementNodeInput> inputs;

    public ElementNode() {
    }

    public ElementNode(String id, String name, String elementType) {
        this.id = id;
        this.name = name;
        this.elementType = elementType;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    public String getElementType() {
        return elementType;
    }

    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public Collection<ElementConnection> getConnectedTo() {
        return connectedTo;
    }

    @Override
    public void setConnectedTo(Collection<ElementConnection> connections) {
        this.connectedTo = Optional.ofNullable(connections).map(ArrayList::new).orElse(null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Map<String, Object> getProperties() {
        return properties;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public List<ElementNodeInput> getInputs() {
        return inputs;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public void setInputs(List<ElementNodeInput> inputs) {
        this.inputs = inputs;
    }

    /* Convenience methods */

    @JsonIgnore
    public Optional<ElementNodeInput> findInput(String name) {
        if (name == null || inputs == null) return Optional.empty();
        return getInputs().stream()
                .filter(input -> name.equals(input.getName()))
                .findFirst();
    }

    @JsonIgnore
    public List<ElementNodeInput> findAllInputs(String name) {
        if (name == null || inputs == null) return new ArrayList<>();
        return getInputs().stream()
                .filter(input -> name.equals(input.getName()))
                .toList();
    }

    @JsonIgnore
    public <B extends AbstractFlowNodeBuilder<B, E>, E extends FlowNode> BpmnModelInstance render(AbstractFlowNodeBuilder<B, E> builder, BpmnComponent elementDefinition, String namespace) {
        return builder.manualTask(id).name(name).done();
    }

    @JsonIgnore
    public void reverseRenderModel(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        extractNodeMetadata(flowNode, namespace);

        if (flowNode.getOutgoing() != null) {
            this.connectedTo = flowNode.getOutgoing().stream()
                    .map(outgoing -> new ElementConnection(outgoing.getTarget().getId(), extractValue(outgoing.getDomElement(), namespace, "name")))
                    .toList();
        }

        List<ElementNodeInput> nodeInputs = reverseRender(flowNode, namespace, componentLibrary, globalVariableLibrary);
        setInputs(nodeInputs.isEmpty() ? new ArrayList<>() : nodeInputs);
    }

    @JsonIgnore
    public void reverseRenderModel(BpmnModelInstance model, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        // Override in subclasses that are not FlowNode-based (e.g. ProcessConfigNode)
    }

    /**
     * Extract all values from a FlowNode's DOM element based on the component library definition
     * Can be overridden to handle special task-specific inputs
     */
    @JsonIgnore
    protected List<ElementNodeInput> reverseRender(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        List<ElementNodeInput> nodeInputs = new ArrayList<>();
        DomElement dom = flowNode.getDomElement();

        BpmnComponent elementDefinition = componentLibrary.getComponentByName(elementType)
                .orElseThrow(() -> new IllegalStateException("No component definition found for element type: " + elementType));

        for (BpmnComponent.InputVariable iv : elementDefinition.getRequiredInputs()) {
            List<ElementNodeInput> extracted = reverseRenderValues(dom, flowNode, namespace, iv);
            nodeInputs.addAll(extracted);
        }
        applyFormatValueToAllInputs(nodeInputs, componentLibrary, globalVariableLibrary);

        return nodeInputs;
    }

    /**
     * Extract input variable values from the DOM. Returns a list because some inputs (like headers) may produce multiple ElementNodeInput entries.
     * If the input variable defines nested properties, they are traversed recursively to build a matching ElementNodeInput with nested properties.
     * Can be overridden to handle special task-specific inputs
     */
    @JsonIgnore
    protected List<ElementNodeInput> reverseRenderValues(DomElement dom, FlowNode flowNode, String namespace, BpmnComponent.InputVariable iv) {
        String inputName = iv.getName();
        String lookupName = getLookupName(iv);

        if (iv.getProperties() != null && !iv.getProperties().isEmpty()) {
            List<ElementNodeInput> result = new ArrayList<>();
            List<DomElement> matchingElements = findChildElements(dom, lookupName);

            for (DomElement childElement : matchingElements) {
                ElementNodeInput entry = new ElementNodeInput();
                entry.setName(inputName);
                entry.setIsProvided(true);

                List<ElementNodeInput> subProperties = new ArrayList<>();
                for (BpmnComponent.InputVariable propDef : iv.getProperties()) {
                    subProperties.addAll(reverseRenderValues(childElement, flowNode, namespace, propDef));
                }
                if (!subProperties.isEmpty()) {
                    entry.setProperties(subProperties);
                    result.add(entry);
                }
            }
            return result;
        }

        // Leaf: extract value from attribute, or child/extension element text
        List<ElementNodeInput> result = new ArrayList<>();
        String value = extractValue(dom, namespace, lookupName);
        if (value != null) {
            result.add(createInputFromAttribute(inputName, value, true));
        }
        return result;
    }

    /**
     * Find all child elements with the given local name, searching both direct children
     * and children nested inside extensionElements blocks.
     */
    @JsonIgnore
    protected List<DomElement> findChildElements(DomElement parent, String elementName) {
        List<DomElement> result = new ArrayList<>();
        for (DomElement child : parent.getChildElements()) {
            if (elementName.equals(child.getLocalName())) {
                result.add(child);
            } else if (EXTENSION_ELEMENTS.equals(child.getLocalName())) {
                for (DomElement ext : child.getChildElements()) {
                    if (elementName.equals(ext.getLocalName())) {
                        result.add(ext);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Extract a leaf value from a DOM element by name: tries attribute, then direct child element text,
     * then extension element text.
     */
    @JsonIgnore
    protected String extractValue(DomElement dom, String namespace, String lookupName) {
        String value = extractAttributeValue(dom, namespace, lookupName);
        if (value != null) return value;

        List<DomElement> children = findChildElements(dom, lookupName);
        if (!children.isEmpty()) {
            return children.get(0).getTextContent(); // at this point there is only one child element
        }
        return null;
    }

    @JsonIgnore
    protected ExtensionElements getExtensionElements(BaseElement elementInstance) {
        ExtensionElements extensionElements = elementInstance.getExtensionElements();
        if (extensionElements == null) {
            extensionElements = elementInstance.getModelInstance().newInstance(ExtensionElements.class);
            elementInstance.setExtensionElements(extensionElements);
        }
        return extensionElements;
    }

    @JsonIgnore
    public void configureTaskMetadata(BaseElement baseElement, String namespace) {
        // Stores node metadata on the model for lossless reverse rendering
        baseElement.setAttributeValueNs(namespace, ATTR_NODE_ID, this.id);
        baseElement.setAttributeValueNs(namespace, ATTR_NODE_NAME, this.name);
        baseElement.setAttributeValueNs(namespace, ATTR_NODE_DESCRIPTION, this.description);
    }

    @JsonIgnore
    protected void extractNodeMetadata(BaseElement baseElement, String namespace) {
        this.elementType = baseElement.getElementType().getTypeName();
        this.id = Optional.ofNullable(extractAttributeValue(baseElement.getDomElement(), namespace, ATTR_NODE_ID)).orElse(baseElement.getId());
        this.name = Optional.ofNullable(extractAttributeValue(baseElement.getDomElement(), namespace, ATTR_NODE_NAME)).orElse(baseElement.getId());
        this.description = Optional.ofNullable(extractAttributeValue(baseElement.getDomElement(), namespace, ATTR_NODE_DESCRIPTION)).orElse(this.elementType);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ElementNode that = (ElementNode) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(elementType, that.elementType) &&
                Objects.equals(description, that.description) &&
                Objects.equals(connectedTo, that.connectedTo) &&
                Objects.equals(properties, that.properties) &&
                Objects.equals(inputs, that.inputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, elementType, description, connectedTo, properties, inputs);
    }

    @JsonIgnore
    public static ElementNode fromFlowNode(FlowNode flowNode, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {
        String elementType = flowNode.getElementType().getTypeName();
        Class<? extends ElementNode> nodeClass = ElementNodeTypeRegistry.getNodeClass(elementType);

        ElementNode elementNode;
        try {
            elementNode = nodeClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            LOG.warning(String.format("No ElementNode class registered for BPMN element type '{%s}' (id='%s')", elementType, flowNode.getId()));
            elementNode = new ElementNode();
        }
        elementNode.reverseRenderModel(flowNode, namespace, componentLibrary, globalVariableLibrary);
        return elementNode;
    }

    @JsonIgnore
    public static ElementNode fromProcess(BpmnModelInstance inputModel, String namespace, BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary) {

        Class<? extends ElementNode> nodeClass = ElementNodeTypeRegistry.getNodeClass(PROCESS_CONFIG);

        ElementNode processConfigNode;
        try {
            processConfigNode = nodeClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            processConfigNode = new ProcessConfigNode();
        }
        processConfigNode.reverseRenderModel(inputModel, namespace, componentLibrary, globalVariableLibrary);
        return processConfigNode;
    }
}
