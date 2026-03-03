package org.rj.modelgen.service;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.rj.modelgen.bpmn.intrep.schema.BpmnIntermediateModelSchema;
import org.rj.modelgen.bpmn.models.generation.BpmnGenerationExecutionModelOptions;
import org.rj.modelgen.bpmn.models.generation.base.BpmnGenerationBaseExecutionModel;
import org.rj.modelgen.bpmn.models.generation.multilevel.BpmnMultiLevelGenerationModel;
import org.rj.modelgen.forms.models.generation.pipeline.FormPipelineGenerationModel;
import org.rj.modelgen.llm.integrations.openai.OpenAIModelInterface;
import org.rj.modelgen.llm.util.Util;
import org.rj.modelgen.service.beans.BpmnGenerationPrompt;
import org.rj.modelgen.service.beans.BpmnGenerationSessionData;
import org.rj.modelgen.service.beans.FormGenerationPrompt;
import org.rj.modelgen.service.beans.FormGenerationSessionData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.rj.modelgen.llm.util.FuncUtil.doVoid;

@SpringBootApplication
@ComponentScan(basePackages = "org.rj")
@RestController
public class CodegenServiceApplication {
	private final ConcurrentMap<String, BpmnGenerationSessionData> sessions;
	private final ConcurrentMap<String, FormGenerationSessionData> formSessions;
	private final BpmnMultiLevelGenerationModel bpmnGenerationModel;
	private final FormPipelineGenerationModel formGenerationModel;

	@Value("${app.tokenPath}")
	private String tokenPath;

	public CodegenServiceApplication() {
		this.sessions = new ConcurrentHashMap<>();
		this.formSessions = new ConcurrentHashMap<>();
		this.bpmnGenerationModel = buildMultiPhaseModel();
		this.formGenerationModel = buildFormGenerationModel();
	}

	private BpmnGenerationBaseExecutionModel buildModel() {
		final var modelInterface = new OpenAIModelInterface.Builder()
				.withApiKeyGenerator(() -> Util.loadStringResource(tokenPath))
				.build();

		final var modelSchema = new BpmnIntermediateModelSchema();

		return BpmnGenerationBaseExecutionModel.create(modelInterface, modelSchema, BpmnGenerationExecutionModelOptions.defaultOptions());
	}

	private BpmnMultiLevelGenerationModel buildMultiPhaseModel() {
		final var modelInterface = new OpenAIModelInterface.Builder()
				.withApiKeyGenerator(() -> Util.loadStringResource(tokenPath))
				.build();

		final var options = BpmnMultiLevelGenerationModel.defaultOptions();

		return BpmnMultiLevelGenerationModel.create(modelInterface, options);
	}

	private FormPipelineGenerationModel buildFormGenerationModel() {
		final var modelInterface = new OpenAIModelInterface.Builder()
				.withApiKeyGenerator(() -> Util.loadStringResource(tokenPath))
				.build();

		final var options = FormPipelineGenerationModel.defaultOptions();

		return FormPipelineGenerationModel.create(modelInterface, options);
	}

	// ---- BPMN Generation Endpoints ----

	@GetMapping("/api/bpmn/generation/session/{id}")
	public BpmnGenerationSessionData getSessionData(
			@PathVariable("id") String id
	) {
		return getSession(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No session exists with that ID"));
	}

	@PutMapping("/api/bpmn/generation/session/{id}")
	public BpmnGenerationSessionData getOrCreateSessionData(
			@PathVariable("id") String id
	) {
		return getOrCreateSession(id);
	}

	@PostMapping("/api/bpmn/generation/session/{id}/prompt")
	public Mono<BpmnGenerationSessionData> prompt(
			@PathVariable("id") String id,
			@RequestBody BpmnGenerationPrompt prompt
	) {
		return bpmnGenerationModel.executeModel(id, prompt.getPrompt(), Map.of())
				.doOnSuccess(result -> {
					if (result.isSuccessful()) {
						System.out.println("Result.success = " + result.isSuccessful());
						System.out.println("Result.generated = " + Bpmn.convertToString(result.getGeneratedBpmn()));
						System.out.println("Result.bpmnValidation = " + String.join(", ", result.getBpmnValidationMessages()));
					}
					else {
						System.err.println("Failed with error: " + result.getLastError().orElse("<unknown error>"));
					}
				})
				.map(res -> Optional.ofNullable(res.getGeneratedBpmn())
						.map(Bpmn::convertToString).orElse(""))
				.map(generatedBpmn -> doVoid(generatedBpmn, bpmn -> getOrCreateSession(id).setCurrentBpmnData(generatedBpmn)))
				.map(__ -> getOrCreateSession(id));
	}

	// ---- Form Generation Endpoints ----

	@GetMapping("/api/forms/generation/session/{id}")
	public FormGenerationSessionData getFormSessionData(
			@PathVariable("id") String id
	) {
		return getFormSession(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No form session exists with that ID"));
	}

	@PutMapping("/api/forms/generation/session/{id}")
	public FormGenerationSessionData getOrCreateFormSessionData(
			@PathVariable("id") String id
	) {
		return getOrCreateFormSession(id);
	}

	@PostMapping("/api/forms/generation/session/{id}/prompt")
	public Mono<FormGenerationSessionData> formPrompt(
			@PathVariable("id") String id,
			@RequestBody FormGenerationPrompt prompt
	) {
		return formGenerationModel.executeModel(id, prompt.getPrompt(), Map.of())
				.doOnSuccess(result -> {
					if (result.isSuccessful()) {
						System.out.println("Form Result.success = " + result.isSuccessful());
						System.out.println("Form Result.generated = " + result.getA2UIOutput());
						System.out.println("Form Result.validation = " + String.join(", ", result.getFormValidationMessages()));
					}
					else {
						System.err.println("Form generation failed with error: " + result.getLastError().orElse("<unknown error>"));
					}
				})
				.map(res -> Optional.ofNullable(res.getA2UIOutput()).orElse(""))
				.map(generatedForm -> doVoid(generatedForm, form -> getOrCreateFormSession(id).setCurrentA2UIData(generatedForm)))
				.map(__ -> getOrCreateFormSession(id));
	}

	// ---- BPMN Session Management ----

	private Optional<BpmnGenerationSessionData> getSession(String id) {
		return Optional.ofNullable(sessions.getOrDefault(id, null));
	}

	private BpmnGenerationSessionData getOrCreateSession(String id) {
		return sessions.computeIfAbsent(id, BpmnGenerationSessionData::new);
	}

	// ---- Form Session Management ----

	private Optional<FormGenerationSessionData> getFormSession(String id) {
		return Optional.ofNullable(formSessions.getOrDefault(id, null));
	}

	private FormGenerationSessionData getOrCreateFormSession(String id) {
		return formSessions.computeIfAbsent(id, FormGenerationSessionData::new);
	}

	public static void main(String[] args) {
		SpringApplication.run(CodegenServiceApplication.class, args);
	}
}
