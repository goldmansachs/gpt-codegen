package org.rj.modelgen.bpmn.intrep.model.common;

import org.camunda.bpm.model.xml.instance.DomElement;
import org.rj.modelgen.bpmn.component.BpmnComponent;
import org.rj.modelgen.bpmn.intrep.model.ElementNodeInput;

import java.security.SecureRandom;

public class ElementNodeSharedUtils {

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] BASE36 = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();

    private static String randomId(int length) {
        char[] out = new char[length];
        for (int i = 0; i < length; i++) {
            out[i] = BASE36[RNG.nextInt(BASE36.length)];
        }
        return new String(out);
    }

    public static String generateRandomId() {
        return randomId(7);
    }

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
