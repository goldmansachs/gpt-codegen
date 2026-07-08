package org.rj.modelgen.bpmn.intrep.model.common;

import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.bpmn.instance.Message;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.rj.modelgen.bpmn.generation.BpmnConstants.ReceiveTaskConstants.MESSAGE_PREFIX;

// Shared utility for resolving or creating top-level BPMN definition elements
public final class BpmnDefinitionElementResolver {

    private BpmnDefinitionElementResolver() { }

    // model-level message definition
    public static Message resolveOrCreateMessage(BpmnModelInstance modelInstance, String messageName) {
        return resolveOrCreateDefinitionElement(
                modelInstance, Message.class,
                existing -> messageName.equals(existing.getName()),
                message -> {
                    message.setId(MESSAGE_PREFIX + UUID.randomUUID().toString().substring(0, 7));
                    message.setName(messageName);
                });
    }

    // model-level error definition
    public static org.camunda.bpm.model.bpmn.instance.Error resolveOrCreateError(BpmnModelInstance modelInstance, String errorCode) {
        return resolveOrCreateDefinitionElement(
                modelInstance, org.camunda.bpm.model.bpmn.instance.Error.class,
                existing -> errorCode.equals(existing.getErrorCode()),
                error -> {
                    error.setId("Error_" + UUID.randomUUID().toString().substring(0, 7));
                    error.setErrorCode(errorCode);
                    error.setName(errorCode);
                });
    }

    private static <T extends BaseElement> T resolveOrCreateDefinitionElement(BpmnModelInstance modelInstance, Class<T> type, Predicate<T> matcher, Consumer<T> initializer) {
        for (T existing : modelInstance.getModelElementsByType(type)) {
            if (matcher.test(existing)) return existing;
        }
        T element = modelInstance.newInstance(type);
        initializer.accept(element);
        modelInstance.getDefinitions().addChildElement(element);
        return element;
    }
}

