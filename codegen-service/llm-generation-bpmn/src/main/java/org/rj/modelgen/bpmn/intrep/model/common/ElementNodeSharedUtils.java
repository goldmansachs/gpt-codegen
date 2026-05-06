package org.rj.modelgen.bpmn.intrep.model.common;

import org.camunda.bpm.model.xml.instance.DomElement;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

public class ElementNodeSharedUtils {


    public static String extractAttributeValue(DomElement dom, String modelNamespace, String requiredName) {
        // check for attribute in provided namespace
        if (modelNamespace != null && dom.hasAttribute(modelNamespace, requiredName)) {
            return dom.getAttribute(modelNamespace, requiredName);
        }

        // check for attribute without namespace if not found in provided namespace
        if (dom.hasAttribute(requiredName)) {
            return dom.getAttribute(requiredName);
        }

        // check for attribute in default namespace if not found in provided namespace
        String namespace = dom.getNamespaceURI();
        if (namespace != null && !namespace.equals(modelNamespace) && dom.hasAttribute(namespace, requiredName)) {
            return dom.getAttribute(namespace, requiredName);
        }
        return null;
    }

    public static String getAttrName(ElementNodeInput input, BpmnComponent elementDefinition) {
        return elementDefinition.getInputVariable(input.getName())
                .map(ElementNodeSharedUtils::getLookupName)
                .orElse(input.getName());
    }

    public static String getLookupName(BpmnComponent.InputVariable iv) {
        return iv.getAlias() != null ? iv.getAlias() : iv.getName();
    }
}
