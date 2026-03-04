package org.rj.modelgen.service;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.rj.modelgen.bpmn.intrep.schema.BpmnIntermediateModelSchema;
import org.rj.modelgen.bpmn.models.generation.BpmnGenerationExecutionModelOptions;
import org.rj.modelgen.bpmn.models.generation.base.BpmnGenerationBaseExecutionModel;
import org.rj.modelgen.bpmn.models.generation.multilevel.BpmnMultiLevelGenerationModel;
import org.rj.modelgen.ui.models.generation.a2ui.A2UIGenerationModel;
import org.rj.modelgen.llm.integrations.openai.OpenAIModelInterface;
import org.rj.modelgen.llm.util.Util;
import org.rj.modelgen.service.beans.BpmnGenerationPrompt;
import org.rj.modelgen.service.beans.BpmnGenerationSessionData;
import org.rj.modelgen.service.beans.UIGenerationPrompt;
import org.rj.modelgen.service.beans.UIGenerationSessionData;
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
	private final ConcurrentMap<String, UIGenerationSessionData> uiSessions;
	private final BpmnMultiLevelGenerationModel bpmnGenerationModel;
	private final A2UIGenerationModel uiGenerationModel;

	@Value("${app.tokenPath}")
	private String tokenPath;

	public CodegenServiceApplication() {
		this.sessions = new ConcurrentHashMap<>();
		this.uiSessions = new ConcurrentHashMap<>();
		this.bpmnGenerationModel = buildMultiPhaseModel();
		this.uiGenerationModel = buildUIGenerationModel();
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

	private A2UIGenerationModel buildUIGenerationModel() {
		final var modelInterface = new OpenAIModelInterface.Builder()
				.withApiKeyGenerator(() -> Util.loadStringResource(tokenPath))
				.build();

		final var options = A2UIGenerationModel.defaultOptions();

		return A2UIGenerationModel.create(modelInterface, options);
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

	// ---- UI Generation Endpoints ----

	@GetMapping("/api/ui/generation/session/{id}")
	public UIGenerationSessionData getUISessionData(
			@PathVariable("id") String id
	) {
		return getUISession(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No UI session exists with that ID"));
	}

	@PutMapping("/api/ui/generation/session/{id}")
	public UIGenerationSessionData getOrCreateUISessionData(
			@PathVariable("id") String id
	) {
		return getOrCreateUISession(id);
	}

	@PostMapping("/api/ui/generation/session/{id}/prompt")
	public Mono<UIGenerationSessionData> uiPrompt(
			@PathVariable("id") String id,
			@RequestBody UIGenerationPrompt prompt
	) {
		return uiGenerationModel.executeModel(id, prompt.getPrompt(), Map.of())
				.doOnSuccess(result -> {
					if (result.isSuccessful()) {
						System.out.println("UI Result.success = " + result.isSuccessful());
						System.out.println("UI Result.generated = " + result.getUIOutput());
						System.out.println("UI Result.validation = " + String.join(", ", result.getUIValidationMessages()));
					}
					else {
						System.err.println("UI generation failed with error: " + result.getLastError().orElse("<unknown error>"));
					}
				})
				.map(res -> Optional.ofNullable(res.getUIOutput()).orElse(""))
				.map(generatedUI -> doVoid(generatedUI, form -> getOrCreateUISession(id).setCurrentUIData(generatedUI)))
				.map(__ -> getOrCreateUISession(id));
	}

	// ---- BPMN Session Management ----

	private Optional<BpmnGenerationSessionData> getSession(String id) {
		return Optional.ofNullable(sessions.getOrDefault(id, null));
	}

	private BpmnGenerationSessionData getOrCreateSession(String id) {
		return sessions.computeIfAbsent(id, BpmnGenerationSessionData::new);
	}

	// ---- UI Session Management ----

	private Optional<UIGenerationSessionData> getUISession(String id) {
		return Optional.ofNullable(uiSessions.getOrDefault(id, null));
	}

	private UIGenerationSessionData getOrCreateUISession(String id) {
		return uiSessions.computeIfAbsent(id, UIGenerationSessionData::new);
	}

	public static void main(String[] args) {
		SpringApplication.run(CodegenServiceApplication.class, args);
	}
}
