package org.rj.modelgen.bpmn.models.generation.multilevel.states;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.rj.modelgen.bpmn.component.BpmnComponentLibrary;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariable;
import org.rj.modelgen.bpmn.component.globalvars.library.BpmnGlobalVariableLibrary;
import org.rj.modelgen.bpmn.models.generation.multilevel.options.BpmnMultiLevelGenerationModelOptions;
import org.rj.modelgen.bpmn.models.generation.validation.PayloadVariable;
import org.rj.modelgen.llm.models.generation.multilevel.data.MultiLevelModelStandardPayloadData;
import org.rj.modelgen.llm.statemodel.states.common.ExecuteLogic;
import org.rj.modelgen.llm.util.Result;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.rj.modelgen.bpmn.models.generation.base.context.BpmnPromptPlaceholders.*;

public class InitializeBpmnData extends ExecuteLogic {
    private static final Logger LOG = Logger.getLogger(InitializeBpmnData.class.getName());
    private static final Pattern JSON_LIST_EXTRACT = Pattern.compile("^.*?(\\[.*]).*?$", Pattern.DOTALL | Pattern.MULTILINE);

    private final BpmnComponentLibrary componentLibrary;
    private final BpmnGlobalVariableLibrary globalVariableLibrary;
    private final BpmnMultiLevelGenerationModelOptions options;

    public InitializeBpmnData(BpmnComponentLibrary componentLibrary, BpmnGlobalVariableLibrary globalVariableLibrary, BpmnMultiLevelGenerationModelOptions options) {
        super(InitializeBpmnData.class);
        this.componentLibrary = componentLibrary;
        this.globalVariableLibrary = globalVariableLibrary;
        this.options = options;
    }

    @Override
    protected Mono<Result<Void, String>> executeLogic() {
        // Insert additional BPMN data into the model payload
        getPayload().put(GLOBAL_VARIABLE_LIBRARY.getValue(), globalVariableLibrary.defaultSerialize());

        if (options.shouldAddPromptSpecificGlobalVariables()) {
            // Insert filtered set of variables detected in the initial prompt, for use in preprocessing phases
            final var prompt = getPayload().getOrElse(MultiLevelModelStandardPayloadData.Request, "");
            final var promptRelevantGlobalVariables = globalVariableLibrary.getFilteredBasedOnPrompt(prompt);
            getPayload().put(PROMPT_RELEVANT_GLOBAL_VARIABLES.getValue(), promptRelevantGlobalVariables.defaultSerialize());
        }

        // Enable use of placeholders for unknown action types if required
        if (options.shouldAddPlaceholderForUnknownComponents()) {
            getPayload().put(MultiLevelModelStandardPayloadData.AddPlaceholdersForUnknownActions, true);
        }

        if (options.shouldAddStartingPayloadVariables()) {
            initializeStartingPayload();
        }

        return Mono.just(Result.Ok());
    }

    private void initializeStartingPayload() {
        final Object processVariables = getPayload().get(MultiLevelModelStandardPayloadData.ProcessVariables);

        if (processVariables == null) {
            getPayload().put(MultiLevelModelStandardPayloadData.ProcessVariables, Collections.<PayloadVariable>emptySet());
            getPayload().remove(STARTING_PAYLOAD_VARIABLES.getValue());
            return;
        }

        final Set<PayloadVariable> startingPayloadVariables = parseAndFilterPayloadVariables(processVariables);
        getPayload().put(MultiLevelModelStandardPayloadData.ProcessVariables, startingPayloadVariables);

        // Also serialize them for use in prompt generation
        final String serializedStartingPayload = startingPayloadVariables.stream()
                .map(v -> String.format("- %s (%s)", v.getName(), v.getType()))
                .collect(Collectors.joining("\n"));
        getPayload().put(STARTING_PAYLOAD_VARIABLES.getValue(), serializedStartingPayload);
    }

    private Set<PayloadVariable> parseAndFilterPayloadVariables(Object rawProcessVariablesContent) {
        if (rawProcessVariablesContent == null) {
            return Collections.emptySet();
        }

        if (rawProcessVariablesContent instanceof Collection<?> existingVariables) {
            LOG.info("Process variables already parsed; filtering existing collection");
            return filterPayloadVariables(existingVariables.stream()
                    .filter(PayloadVariable.class::isInstance)
                    .map(PayloadVariable.class::cast)
                    .collect(Collectors.toList()));
        }

        if (!(rawProcessVariablesContent instanceof String processVariablesString)) {
            LOG.warning("Unexpected process variables type: " + rawProcessVariablesContent.getClass().getName() + "; defaulting to empty set");
            return Collections.emptySet();
        }

        String processVariablesContent = extractJsonList(processVariablesString);
        List<PayloadVariable> processVariablesList;
        try {
            ObjectMapper mapper = new ObjectMapper();
            processVariablesList = mapper.readValue(processVariablesContent, new TypeReference<>() {});

        } catch (Exception e) {
            LOG.warning(String.format("Failed to parse process variables content: %s. Error: %s", processVariablesContent, e.getMessage()));
            processVariablesList = Collections.emptyList();
        }

        return filterPayloadVariables(processVariablesList);
    }

    private Set<PayloadVariable> filterPayloadVariables(List<PayloadVariable> processVariablesList) {
        List<PayloadVariable> automaticallyGeneratedOutputs = componentLibrary.getComponents().stream()
                .filter(component -> component.getGeneratedOutputs() != null)
                .flatMap(component -> component.getGeneratedOutputs().stream())
                .map(variable -> new PayloadVariable(variable.getName(), variable.getType().toString()))
                .toList();

        Set<String> globalVarResolveValues = globalVariableLibrary.getComponents().stream()
                .map(BpmnGlobalVariable::getResolveValue)
                .collect(Collectors.toSet());

        // Compute starting payload by removing automatically generated outputs and global vars from the read variables
        return processVariablesList.stream()
                .filter(x -> !automaticallyGeneratedOutputs.contains(x) && !globalVarResolveValues.contains(x.getName()))
                .collect(Collectors.toSet());
    }

    private String extractJsonList(String content) {
        final var matcher = JSON_LIST_EXTRACT.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return content;
    }
}
