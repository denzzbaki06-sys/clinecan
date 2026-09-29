package com.clinecan.backend.service;
import com.clinecan.backend.agent.OutputValidator;
import com.clinecan.backend.llm.*;
import com.clinecan.backend.model.*;
import com.clinecan.backend.sandbox.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.annotation.PreDestroy;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class ExecutionService {
    public record Progress(long sequence, String executionId, String timestamp, String stage, String status, String message) {}
    public record PreviewArtifact(boolean available, String kind, String downloadUrl, long bytes, String entryUrl) {}
    public record Snapshot(String id, String status, String generationMode, List<Progress> events, AgentResponse result, BuildResult build, PreviewArtifact preview, AgentError error, int repairAttempts) {}
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED", "CANCELLED", "TIMED_OUT");
    private final Clock clock;
    private final AgentService generator; private final LlmClient llm; private final SandboxService sandbox; private final SandboxSettings settings;
    private final Map<String, Execution> executions = new LinkedHashMap<>();
    private final ThreadPoolExecutor workers;
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("clinecan-cleanup").factory());
    private static final int MAX_RETAINED = 20;
    private static class Execution {
        final String id = UUID.randomUUID().toString(); final long created;
        final String previewToken = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String status = "CREATED"; final String mode; final List<Progress> events = new ArrayList<>();
        final List<SseEmitter> subscribers = new ArrayList<>(); AgentResponse result; BuildResult build; byte[] artifact; AgentError error; int repairs; Future<?> future;
        Execution(String mode, long created) { this.mode = mode; this.created = created; }
        boolean terminal() { return TERMINAL.contains(status); }
        synchronized Snapshot snapshot() { return new Snapshot(id, status, mode, List.copyOf(events), result, build,
            new PreviewArtifact(artifact != null && "COMPLETED".equals(status), "SANDBOXED_IFRAME", artifact == null ? null : "/api/agent/executions/" + id + "/artifact", artifact == null ? 0 : artifact.length, artifact == null || !"COMPLETED".equals(status) ? null : "/api/preview/" + id + "/" + previewToken + "/index.html"), error, repairs); }
        synchronized void emit(String stage, String status, String message) {
            if (terminal()) return;
            var p = new Progress(events.size() + 1, id, Instant.now().toString(), stage, status, Diagnostics.safe(message));
            if (events.size() >= 100) throw new IllegalStateException("Event limit");
            events.add(p);
            for (var s : List.copyOf(subscribers)) try { s.send(SseEmitter.event().id(Long.toString(p.sequence())).name("progress").data(p)); }
                catch (Exception ignored) { subscribers.remove(s); s.complete(); }
        }
        synchronized void finish(String state, AgentError failure) {
            if (terminal()) return;
            emit("FINISH", state.equals("COMPLETED") ? "COMPLETED" : "FAILED", state.equals("COMPLETED") ? "Can halletti. ☕️" : "Clinecan stopped: " + state);
            status = state; error = failure;
            for (var s : List.copyOf(subscribers)) try { s.send(SseEmitter.event().id(Long.toString(events.size() + 1)).name("complete").data(snapshot())); s.complete(); }
                catch (Exception ignored) { s.complete(); }
            subscribers.clear();
        }
    }
    @org.springframework.beans.factory.annotation.Autowired
    public ExecutionService(AgentService generator, LlmClient llm, SandboxService sandbox, SandboxSettings settings) {
        this(generator, llm, sandbox, settings, Clock.systemUTC());
    }
    public ExecutionService(AgentService generator, LlmClient llm, SandboxService sandbox, SandboxSettings settings, Clock clock) {
        this.clock = clock;
        this.generator = generator; this.llm = llm; this.sandbox = sandbox; this.settings = settings;
        workers = new ThreadPoolExecutor(settings.concurrent(), settings.concurrent(), 0, TimeUnit.SECONDS, new SynchronousQueue<>(), Thread.ofPlatform().name("clinecan-execution-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());
        cleanup.scheduleAtFixedRate(this::purge, 30, 30, TimeUnit.SECONDS);
    }
    public synchronized Snapshot create(String prompt) {
        if (prompt == null || prompt.isBlank() || prompt.length() > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        purge();
        if (executions.size() >= MAX_RETAINED) {
            var old = executions.values().stream().filter(e -> { synchronized(e) { return e.terminal(); } }).findFirst();
            if (old.isPresent()) executions.remove(old.get().id); else throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
        }
        var e = new Execution(llm.generationMode(), clock.millis()); executions.put(e.id, e);
        e.emit("INITIALIZE", "COMPLETED", "Clinecan initialized. Generation mode: " + e.mode);
        try { e.future = workers.submit(() -> run(e, prompt)); }
        catch (RejectedExecutionException failure) { executions.remove(e.id); throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Agent capacity reached"); }
        return e.snapshot();
    }
    private synchronized Execution get(String id) {
        var e = executions.get(id);
        if (e != null && clock.millis() - e.created > 1_200_000) { executions.remove(id); throw new ResponseStatusException(HttpStatus.GONE); }
        if (e == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return e;
    }
    public Snapshot state(String id) { return get(id).snapshot(); }
    public Snapshot cancel(String id) {
        var e = get(id); synchronized(e) { if (!e.terminal()) { e.finish("CANCELLED", new AgentError("CANCELLED", "İşlem iptal edildi.", false)); if (e.future != null) e.future.cancel(true); } return e.snapshot(); }
    }
    public byte[] artifact(String id) {
        var e = get(id); synchronized(e) { if (!"COMPLETED".equals(e.status) || e.artifact == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return e.artifact.clone(); }
    }
    public PreviewAssets.Asset preview(String id, String token, String path) {
        var e = get(id);
        synchronized(e) {
            if (token == null || !java.security.MessageDigest.isEqual(e.previewToken.getBytes(java.nio.charset.StandardCharsets.UTF_8), token.getBytes(java.nio.charset.StandardCharsets.UTF_8)) || !"COMPLETED".equals(e.status) || e.artifact == null || e.build == null || !e.build.success() || e.result == null || e.result.validation() == null || !e.result.validation().valid())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            if (!PreviewAssets.safe(path)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            var asset = PreviewAssets.read(e.artifact).get(path);
            if (asset == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            return new PreviewAssets.Asset(asset.bytes().clone(), asset.contentType());
        }
    }
    public SseEmitter subscribe(String id, long after) {
        var e = get(id); var emitter = new SseEmitter(900_000L);
        synchronized(e) {
            if (e.subscribers.size() >= 4) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            if (after < 0 || after > e.events.size() + 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            Runnable remove = () -> { synchronized(e) { e.subscribers.remove(emitter); } };
            emitter.onCompletion(remove); emitter.onTimeout(() -> { remove.run(); emitter.complete(); }); emitter.onError(x -> remove.run());
            try {
                for (var p : e.events) if (p.sequence() > after) emitter.send(SseEmitter.event().id(Long.toString(p.sequence())).name("progress").data(p));
                if (e.terminal()) { emitter.send(SseEmitter.event().id(Long.toString(e.events.size()+1)).name("complete").data(e.snapshot())); emitter.complete(); }
                else e.subscribers.add(emitter);
            } catch (Exception failure) { emitter.complete(); }
        }
        return emitter;
    }
    private void run(Execution e, String prompt) {
        try {
            synchronized(e) { if (e.terminal()) return; e.status = "RUNNING"; }
            var generated = generator.run(prompt, e.id, event -> {
                if (!Set.of("INITIALIZE", "FINISH").contains(event.stage())) e.emit(event.stage(), event.status(), event.message());
                if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            });
            synchronized(e) { if (e.terminal()) return; e.result = generated; }
            if (generated.error() != null) { e.finish("FAILED", generated.error()); return; }
            var files = generated.files();
            for (int attempt = 0; attempt <= settings.repairAttempts(); attempt++) {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                synchronized(e) { if (e.terminal()) return; }
                String stage = attempt == 0 ? "BUILD" : "REBUILD";
                e.emit(stage, "RUNNING", attempt == 0 ? "Building generated project in disposable sandbox..." : "Rebuilding repaired files...");
                var outcome = sandbox.build(files);
                synchronized(e) { if (e.terminal()) return; e.build = outcome.build(); }
                if (outcome.build().success() && outcome.artifact() != null) {
                    e.emit(stage, "COMPLETED", "Build successful. TypeScript and Vite verified in sandbox.");
                    synchronized(e) {
                        if (e.terminal()) return;
                        e.artifact = outcome.artifact();
                        e.result = new AgentResponse(generated.projectName(), prompt, "COMPLETED", generated.steps(), List.copyOf(files), generated.generationMode(), generated.summary(), generated.specification(), generated.plan(), generated.validation(), generated.execution(), null);
                    }
                    e.emit("PREVIEW", "COMPLETED", "Preview artifact ready. Isolated visual preview and compiled ZIP available.");
                    e.finish("COMPLETED", null); return;
                }
                e.emit(stage, "FAILED", "Build failed: " + outcome.build().failureCategory());
                if ("BUILD_TIMEOUT".equals(outcome.build().failureCategory())) { e.finish("TIMED_OUT", new AgentError("BUILD_TIMEOUT", "İzole derleme zaman aşımına uğradı.", true)); return; }
                if (!"COMPILATION".equals(outcome.build().failureCategory()) || !"LLM".equals(e.mode) || attempt == settings.repairAttempts()) {
                    e.finish("FAILED", new AgentError(outcome.build().failureCategory() == null ? "BUILD_FAILED" : outcome.build().failureCategory(), "İzole derleme tamamlanamadı. DEMO otomatik AI onarımı yapmaz; LLM onarımı en fazla iki denemedir.", false)); return;
                }
                synchronized(e) { if (e.terminal()) return; e.status = "REPAIRING"; e.repairs = attempt + 1; }
                String diagnostics = Diagnostics.safe(outcome.build().stdout() + "\n" + outcome.build().stderr());
                e.emit("DIAGNOSE", "COMPLETED", "Bounded compiler diagnostics prepared for repair.");
                e.emit("REPAIR", "RUNNING", "Repair attempt " + (attempt+1) + "/" + settings.repairAttempts());
                var patch = llm.repairProject(generated.specification(), generated.plan(), files, diagnostics);
                files = merge(files, patch, generated.plan());
                e.emit("REPAIR", "COMPLETED", patch.files().size() + " files updated and revalidated.");
                synchronized(e) { if (!e.terminal()) e.status = "RUNNING"; }
            }
        } catch (ProviderException failure) { e.finish("FAILED", failure.error()); }
        catch (CancellationException failure) { e.finish("CANCELLED", new AgentError("CANCELLED", "İşlem iptal edildi.", false)); }
        catch (RuntimeException failure) { e.finish("FAILED", new AgentError("EXECUTION_FAILED", "İşlem güvenli biçimde tamamlanamadı.", false)); }
    }
    public static List<GeneratedFile> merge(List<GeneratedFile> current, RepairPatch patch, ProjectPlan plan) {
        if (patch == null || patch.files() == null || patch.files().isEmpty() || patch.files().size() > 32) throw ProviderException.malformed();
        var merged = new LinkedHashMap<String, GeneratedFile>(); current.forEach(f -> merged.put(f.path(), f));
        Set<String> seen = new HashSet<>();
        for (var f : patch.files()) {
            if (f == null || !new OutputValidator().safePath(f.path()) || !merged.containsKey(f.path()) || !seen.add(f.path().toLowerCase(Locale.ROOT)))
                throw new ProviderException("UNSAFE_REPAIR", "Onarım manifest dışı veya yinelenen dosya içeriyor.", false);
            merged.put(f.path(), f);
        }
        var files = List.copyOf(merged.values());
        if (!new OutputValidator().validate(files, plan.fileManifest()).valid()) throw new ProviderException("UNSAFE_REPAIR", "Onarım dosyaları doğrulamadan geçemedi.", false);
        new DependencyPolicy().validate(files); return files;
    }
    public synchronized void purge() {
        long now = clock.millis();
        for (var e : executions.values()) synchronized(e) {
            if (!e.terminal() && now - e.created > 900_000) { e.finish("TIMED_OUT", new AgentError("EXECUTION_TIMEOUT", "İşlem zaman sınırını aştı.", true)); if(e.future != null) e.future.cancel(true); }
        }
        executions.values().removeIf(e -> { synchronized(e) { return e.terminal() && now - e.created > 1_200_000; } });
    }
    @PreDestroy public void close() { cleanup.shutdownNow(); workers.shutdownNow(); synchronized(this) { for(var e:executions.values()) e.finish("CANCELLED", new AgentError("SERVER_STOPPED", "Sunucu durduruldu.", true)); } }
}
