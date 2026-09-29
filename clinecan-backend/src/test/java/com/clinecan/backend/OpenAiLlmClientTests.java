package com.clinecan.backend;

import com.clinecan.backend.agent.*;
import com.clinecan.backend.llm.*;
import com.clinecan.backend.model.*;
import com.clinecan.backend.service.AgentService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiLlmClientTests {
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;
    private int status = 200;
    private String body;
    private long delay;
    private String fixturePrompt = "expense dashboard";
    private String extraPath;
    private java.util.function.UnaryOperator<GeneratedProject> changeProject = p -> p;
    private final List<String> requests = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();
    private final DemoLlmClient demo = new DemoLlmClient(new PromptAnalyzer(), new ProjectPlanner(), new CodeGenerator());
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(executor);
        server.createContext("/v1/responses", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            try { Thread.sleep(delay); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            String output = body;
            if (output == null) {
                var spec = demo.analyzeRequirements(fixturePrompt); var plan = demo.createProjectPlan(spec);
                var project = demo.generateProjectFiles(spec, plan);
                if (extraPath != null) {
                    var paths = new ArrayList<>(plan.fileManifest()); paths.add(extraPath);
                    plan = new ProjectPlan(plan.tasks(), paths);
                    var files = new ArrayList<>(project.files()); files.add(new GeneratedFile(extraPath, "export const fixture = '" + spec.projectName() + "';"));
                    project = new GeneratedProject(project.projectName(), project.summary(), files);
                }
                Object value = switch(requests.size()) { case 1 -> spec; case 2 -> plan; default -> changeProject.apply(project); };
                output = envelope(StructuredJson.MAPPER.writeValueAsString(value));
            }
            byte[] bytes = output.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        }); server.start();
    }
    @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }
    private OpenAiLlmClient client(Duration timeout) { return new OpenAiLlmClient("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/responses", "fixture-secret", "test-model", timeout); }
    private String envelope(String content) { return StructuredJson.MAPPER.writeValueAsString(Map.of("status", "completed", "output", List.of(Map.of("type", "message", "content", List.of(Map.of("type", "output_text", "text", content)))))); }
    @Test void threeStructuredCallsAndLlmSuccess() {
        var result = new AgentService(client(Duration.ofSeconds(5)), new OutputValidator()).run("expense dashboard");
        assertEquals("COMPLETED", result.status(), String.valueOf(result.error())); assertEquals("LLM", result.generationMode()); assertEquals(3, requests.size());
        for (String request : requests) { assertTrue(request.contains("json_schema")); assertTrue(request.contains("\"strict\":true")); assertFalse(request.contains("fixture-secret")); }
        assertTrue(requests.get(1).contains("fileManifest")); assertTrue(requests.get(2).contains("expense-tracker"));
        assertTrue(requests.get(2).contains("originalPrompt"));
        assertEquals(List.of("Bearer fixture-secret", "Bearer fixture-secret", "Bearer fixture-secret"), authorizations);
    }
    @ParameterizedTest @CsvSource({"401,PROVIDER_AUTH", "403,PROVIDER_AUTH", "429,PROVIDER_RATE_LIMIT", "500,PROVIDER_UNAVAILABLE", "503,PROVIDER_UNAVAILABLE", "400,PROVIDER_REQUEST", "302,PROVIDER_REQUEST"})
    void sanitizedProviderErrors(int status, String code) {
        this.status = status; body = "fixture-secret internal error details";
        var error = assertThrows(ProviderException.class, () -> client(Duration.ofSeconds(2)).analyzeRequirements("test"));
        assertEquals(code, error.error().code()); assertFalse(error.getMessage().contains("fixture-secret"));
        assertEquals(1, requests.size(), "No implicit retries or paid-call amplification");
    }
    @Test void timeoutIsBounded() {
        delay = 500; body = "{}";
        assertEquals("PROVIDER_TIMEOUT", assertThrows(ProviderException.class, () -> client(Duration.ofMillis(50)).analyzeRequirements("test")).error().code());
    }
    @Test void malformedJson() {
        body = envelope("not valid json");
        assertEquals("INVALID_OUTPUT", assertThrows(ProviderException.class, () -> client(Duration.ofSeconds(2)).analyzeRequirements("test")).error().code());
    }
    @Test void truncatedGenerationRejected() {
        body = "{\"status\":\"incomplete\",\"output\":[]}";
        assertEquals("INCOMPLETE_OUTPUT", assertThrows(ProviderException.class, () -> client(Duration.ofSeconds(2)).analyzeRequirements("test")).error().code());
    }
    @Test void refusalIsHandled() {
        body = "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"No\"}]}]}";
        assertEquals("PROVIDER_REFUSAL", assertThrows(ProviderException.class, () -> client(Duration.ofSeconds(2)).analyzeRequirements("test")).error().code());
    }
    @Test void oversizedHttpBodyRejected() {
        body = "x".repeat(2_000_001);
        assertEquals("OUTPUT_TOO_LARGE", assertThrows(ProviderException.class, () -> client(Duration.ofSeconds(5)).analyzeRequirements("test")).error().code());
    }
    @Test void echoedCredentialIsBlocked() {
        body = envelope("fixture-secret");
        assertEquals("SECRET_OUTPUT", assertThrows(ProviderException.class, () -> client(Duration.ofSeconds(2)).analyzeRequirements("test")).error().code());
    }
    @Test void rejectsInsecureRemoteEndpoint() {
        var client = new OpenAiLlmClient("http://example.com/v1/responses", "fixture-secret", "test-model", Duration.ofSeconds(2));
        assertEquals("PROVIDER_CONFIG", assertThrows(ProviderException.class, () -> client.analyzeRequirements("test")).error().code());
        assertTrue(requests.isEmpty());
    }
    @Test void requestSensitiveSdkFixturesAndOriginalPrompt() {
        var prompts = List.of(
            "Create a personal expense tracking dashboard with categories, charts and monthly analytics.",
            "Create a modern portfolio website for a software engineering student with projects, skills and contact sections.",
            "Create a task management application with priorities, deadlines and status filters.");
        var extras = List.of("src/components/Analytics.tsx", "src/components/Projects.tsx", "src/types/task.ts");
        var results = new ArrayList<AgentResponse>();
        var provider = client(Duration.ofSeconds(5));
        for (int i = 0; i < prompts.size(); i++) {
            fixturePrompt = prompts.get(i); extraPath = extras.get(i); requests.clear(); authorizations.clear();
            var result = new AgentService(provider, new OutputValidator()).run(fixturePrompt);
            assertEquals("COMPLETED", result.status(), String.valueOf(result.error()));
            assertEquals("LLM", result.generationMode());
            assertTrue(result.files().stream().anyMatch(f -> f.path().equals(extraPath)));
            var payload = StructuredJson.MAPPER.readTree(requests.get(2));
            var input = StructuredJson.MAPPER.readTree(payload.path("input").asText());
            assertEquals(fixturePrompt, input.path("originalPrompt").asText());
            assertTrue(input.has("specification")); assertTrue(input.has("plan"));
            assertFalse(payload.path("store").asBoolean());
            assertEquals(16000, payload.path("max_output_tokens").asInt());
            var format = payload.path("text").path("format");
            assertTrue(format.path("strict").asBoolean());
            assertTrue(format.path("schema").path("properties").has("files"));
            results.add(result);
        }
        assertEquals(3, results.stream().map(AgentResponse::plan).distinct().count());
        assertEquals(3, results.stream().map(AgentResponse::files).distinct().count());
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"unsafe", "duplicate", "oversized", "count", "empty", "total"})
    void sdkFilesAreStillValidated(String problem) {
        changeProject = p -> {
            var files = new ArrayList<>(p.files());
            switch (problem) {
                case "unsafe" -> files.set(0, new GeneratedFile("../escape", "blocked"));
                case "duplicate" -> files.add(files.getFirst());
                case "oversized" -> files.set(0, new GeneratedFile(files.getFirst().path(), "x".repeat(150001)));
                case "count" -> { for (int i = 0; i < 33; i++) files.add(new GeneratedFile("src/extra" + i + ".ts", "export {};")); }
                case "empty" -> files.clear();
                case "total" -> { for (int i = 0; i < 7; i++) files.add(new GeneratedFile("src/extra" + i + ".ts", "x".repeat(149000))); }
            }
            return new GeneratedProject(p.projectName(), p.summary(), files);
        };
        var result = new AgentService(client(Duration.ofSeconds(5)), new OutputValidator()).run(fixturePrompt);
        assertEquals("FAILED", result.status()); assertEquals("LLM", result.generationMode());
        assertTrue(result.files().isEmpty()); assertNotNull(result.error());
    }
    @Test void malformedEnvelopeIsDifferentFromInvalidStructuredOutput() {
        body = "not json";
        assertEquals("PROVIDER_RESPONSE", assertThrows(ProviderException.class, () -> client(Duration.ofSeconds(2)).analyzeRequirements("test")).error().code());
    }
    @Test void invalidConfigurationDoesNotFallBack() {
        var result = new AgentService(client(Duration.ZERO), new OutputValidator()).run("test");
        assertEquals("LLM", result.generationMode()); assertEquals("PROVIDER_CONFIG", result.error().code());
        assertTrue(requests.isEmpty());
    }

    @Test void repairUsesStructuredPatchAndBoundedDiagnostics() {
        var spec=demo.analyzeRequirements("task");var plan=demo.createProjectPlan(spec);var files=demo.generateProjectFiles(spec,plan).files();
        body=envelope(StructuredJson.MAPPER.writeValueAsString(new RepairPatch(List.of(files.getFirst()))));
        var patch=client(Duration.ofSeconds(5)).repairProject(spec,plan,files,"TS2322 token=private " + "x".repeat(12000));
        assertEquals(1,patch.files().size());
        var payload=StructuredJson.MAPPER.readTree(requests.getFirst());var input=StructuredJson.MAPPER.readTree(payload.path("input").asText());
        assertEquals("REPAIR",input.path("stage").asText());assertTrue(input.path("diagnostics").asText().length()<=8000);
        assertFalse(input.path("diagnostics").asText().contains("private"));assertTrue(input.has("currentFiles"));assertTrue(payload.path("text").path("format").path("strict").asBoolean());
    }
}
