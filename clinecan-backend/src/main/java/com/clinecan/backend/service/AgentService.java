package com.clinecan.backend.service;

import com.clinecan.backend.agent.*;
import com.clinecan.backend.llm.*;
import com.clinecan.backend.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** Synchronous orchestrator. Execution events can later be forwarded to a streaming transport. */
@Service
public class AgentService {
    private final LlmClient client;
    private final OutputValidator validator;
    private static final List<String> STAGES = List.of("ANALYZE", "PLAN", "GENERATE", "VALIDATE");
    @Autowired
    public AgentService(LlmClient client, OutputValidator validator) { this.client = client; this.validator = validator; }
    /** Compatibility constructor for the original pipeline tests. */
    public AgentService(PromptAnalyzer analyzer, ProjectPlanner planner, CodeGenerator generator, OutputValidator validator) {
        this(new DemoLlmClient(analyzer, planner, generator), validator);
    }
    public String generationMode() { return client.generationMode(); }
    public AgentResponse run(String prompt) {
        return run(prompt, UUID.randomUUID().toString(), event -> {});
    }
    public AgentResponse run(String prompt, String id, java.util.function.Consumer<AgentEvent> listener) {
        if (prompt == null || prompt.isBlank() || prompt.length() > 10000) throw new IllegalArgumentException("Invalid prompt");
        List<AgentStep> steps = new ArrayList<>(STAGES.stream().map(s -> new AgentStep(s, "PENDING", "Henüz çalıştırılmadı.")).toList());
        List<AgentEvent> events = new ArrayList<>() {
            @Override public boolean add(AgentEvent e) { listener.accept(e); return super.add(e); }
        };
        events.add(event("INITIALIZE", "Clinecan initialized. Generation mode: " + generationMode(), "COMPLETED"));
        ProjectSpecification spec = null; ProjectPlan plan = null; GeneratedProject project = null;
        ValidationResult validation = null; AgentError error = null;
        int stage = 0; long started = System.nanoTime();
        try {
            events.add(event("ANALYZE", "Analyzing requirements", "RUNNING"));
            spec = ContractValidator.specification(client.analyzeRequirements(prompt.trim()));
            complete(steps, events, stage, started, "Requirements understood: " + spec.features().size() + " features.");
            stage = 1; started = System.nanoTime(); events.add(event("PLAN", "Planning project and file manifest", "RUNNING"));
            plan = ContractValidator.plan(client.createProjectPlan(spec));
            complete(steps, events, stage, started, plan.tasks().size() + " tasks planned; " + plan.fileManifest().size() + " file paths verified.");
            stage = 2; started = System.nanoTime(); events.add(event("GENERATE", "Generating project files", "RUNNING"));
            project = ContractValidator.project(client.generateProjectFiles(prompt, spec, plan), spec.projectName());
            complete(steps, events, stage, started, project.files().size() + " files returned; awaiting validation.");
            stage = 3; started = System.nanoTime(); events.add(event("VALIDATE", "Validating manifest and generated content", "RUNNING"));
            validation = validator.validate(project.files(), plan.fileManifest());
            if (!validation.valid()) throw new ProviderException("VALIDATION_FAILED", "Üretilen proje statik doğrulamadan geçemedi. Dosyalar yayımlanmadı.", false);
            complete(steps, events, stage, started, "File manifest and content verified. No build or shell commands executed.");
        } catch (ProviderException failure) {
            error = failure.error();
        } catch (RuntimeException failure) {
            error = new AgentError("EXECUTION_FAILED", "Proje oluşturulamadı. Lütfen tekrar dene.", false);
        }
        if (error != null) {
            steps.set(stage, new AgentStep(STAGES.get(stage), "FAILED", error.message(), elapsed(started), error));
            events.add(event(STAGES.get(stage), error.message(), "FAILED"));
        }
        events.add(event("FINISH", error == null ? "Clinecan finished" : "Clinecan stopped: " + error.code(), error == null ? "COMPLETED" : "FAILED"));
        return new AgentResponse(spec == null ? "clinecan-project" : spec.projectName(), prompt, error == null ? "COMPLETED" : "FAILED",
            List.copyOf(steps), error == null ? List.copyOf(project.files()) : List.of(), generationMode(),
            error == null ? project.summary() : "", spec, plan, validation, new AgentExecution(id, List.copyOf(events)), error);
    }
    private static long elapsed(long started) { return Math.max(0, (System.nanoTime() - started) / 1_000_000); }
    private static AgentEvent event(String stage, String message, String status) { return new AgentEvent(Instant.now().toString(), stage, message, status); }
    private static void complete(List<AgentStep> steps, List<AgentEvent> events, int stage, long started, String message) {
        steps.set(stage, new AgentStep(STAGES.get(stage), "COMPLETED", message, elapsed(started), null));
        events.add(event(STAGES.get(stage), message, "COMPLETED"));
    }
}
