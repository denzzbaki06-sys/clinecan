package com.clinecan.backend;

import com.clinecan.backend.agent.*;
import com.clinecan.backend.llm.*;
import com.clinecan.backend.model.*;
import com.clinecan.backend.service.AgentService;
import com.clinecan.backend.config.LlmConfiguration;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PhaseTwoTests {
    private final OutputValidator validator = new OutputValidator();
    private final DemoLlmClient demo = new DemoLlmClient(new PromptAnalyzer(), new ProjectPlanner(), new CodeGenerator());
    @ParameterizedTest
    @ValueSource(strings = {"../x", "src/../../x", "/etc/passwd", "~/secret", "C:/Windows/x", "C:\\Windows\\x", "src\\x", "src/./x", "src//x", "src/../x", ".env", "src/.env.local", ".git/config", "etc/passwd", "Users/user/x", "src/id_rsa", "keys/token.pem", "node_modules/x", "src/App.tsx/", "src/nul.txt", "src/%2e%2e/x", "src/x\u0000", "src/x.", ""})
    void rejectsUnsafePaths(String path) { assertFalse(validator.safePath(path), path); }
    @Test void acceptsCanonicalPaths() { assertTrue(validator.safePath("src/components/ExpenseTable.tsx")); }
    @Test void rejectsCaseInsensitiveDuplicates() {
        var paths = new ArrayList<>(OutputValidator.REQUIRED); paths.add("src/app.tsx");
        assertFalse(validator.validateManifest(paths).valid());
    }
    @Test void rejectsFileFolderCollision() {
        var paths = new ArrayList<>(OutputValidator.REQUIRED); paths.add("src");
        assertFalse(validator.validateManifest(paths).valid());
    }
    @Test void rejectsEmptyAndOversizedContent() {
        for (String content : List.of("", " ", "x".repeat(150001))) {
            var files = new ArrayList<>(new CodeGenerator().generate("test")); files.set(0, new GeneratedFile("src/App.tsx", content));
            assertFalse(validator.isValid(files));
        }
    }
    @Test void rejectsManifestMismatch() {
        var files = new CodeGenerator().generate("test"); var manifest = new ArrayList<>(OutputValidator.REQUIRED); manifest.add("src/extra.ts");
        assertFalse(validator.validate(files, manifest).valid());
    }
    @Test void demoIsDeterministicAndRequestSensitive() {
        var service = new AgentService(demo, validator);
        var expense = service.run("expense dashboard categories monthly analytics");
        var portfolio = service.run("portfolio projects skills contact");
        var task = service.run("task priorities status filters");
        assertEquals("DEMO", expense.generationMode()); assertEquals("COMPLETED", expense.status());
        assertEquals(expense.files(), service.run("expense dashboard categories monthly analytics").files());
        assertNotEquals(expense.files(), portfolio.files()); assertNotEquals(task.files(), portfolio.files());
        assertTrue(expense.files().getFirst().content().contains("Monthly analytics"));
        assertTrue(portfolio.files().getFirst().content().contains("Selected projects"));
        assertTrue(task.files().getFirst().content().contains("Status"));
        assertTrue(task.files().getFirst().content().contains("Deadline"));
        assertTrue(expense.files().getFirst().content().contains("Category chart"));
        assertTrue(expense.validation().valid()); assertEquals(10, expense.execution().events().size());
    }
    @Test void missingCredentialSelectsDemo() {
        var client = new LlmConfiguration().llmClient(new MockEnvironment(), new PromptAnalyzer(), new ProjectPlanner(), new CodeGenerator());
        assertEquals("DEMO", client.generationMode());
    }
    @Test void configuredCredentialSelectsLlmWithoutNetwork() {
        var client = new LlmConfiguration().llmClient(new MockEnvironment().withProperty("clinecan.llm.api-key", "test-only"), new PromptAnalyzer(), new ProjectPlanner(), new CodeGenerator());
        assertEquals("LLM", client.generationMode());
    }
    @Test void specificationParsing() {
        var source = demo.analyzeRequirements("expense tracker");
        var parsed = StructuredJson.parse(StructuredJson.MAPPER.writeValueAsString(source), ProjectSpecification.class);
        assertEquals(source, ContractValidator.specification(parsed));
    }
    @ParameterizedTest
    @ValueSource(strings={"not json", "```json\n{}\n```", "{}", "{\"projectName\":null}", "{\"a\":1,\"a\":2}", "{} trailing"})
    void malformedSpecificationRejected(String json) { assertThrows(ProviderException.class, () -> ContractValidator.specification(StructuredJson.parse(json, ProjectSpecification.class))); }
    @Test void rejectsUnknownFieldsAndCoercion() {
        String json = StructuredJson.MAPPER.writeValueAsString(demo.analyzeRequirements("test"));
        assertThrows(ProviderException.class, () -> StructuredJson.parse(json.replace("\"backendRequired\":false", "\"backendRequired\":\"false\""), ProjectSpecification.class));
        assertThrows(ProviderException.class, () -> StructuredJson.parse(json.replaceFirst("\\{", "{\"extra\":true,"), ProjectSpecification.class));
    }
    @Test void orchestratorReportsProviderFailureWithoutSecretsOrFallback() {
        LlmClient broken = new LlmClient() {
            public String generationMode() { return "LLM"; }
            public ProjectSpecification analyzeRequirements(String prompt) { return demo.analyzeRequirements(prompt); }
            public ProjectPlan createProjectPlan(ProjectSpecification spec) { throw new ProviderException("PROVIDER_RATE_LIMIT", "Rate limited", true); }
            public GeneratedProject generateProjectFiles(ProjectSpecification spec, ProjectPlan plan) { fail("Must not generate after plan failure"); return null; }
        };
        var response = new AgentService(broken, validator).run("test");
        assertEquals("LLM", response.generationMode()); assertEquals("FAILED", response.status());
        assertEquals(List.of("COMPLETED", "FAILED", "PENDING", "PENDING"), response.steps().stream().map(AgentStep::status).toList());
        assertTrue(response.files().isEmpty()); assertTrue(response.error().retryable());
    }
    @Test void orchestratorWithholdsUnsafeFiles() {
        var broken = new DemoLlmClient(new PromptAnalyzer(), new ProjectPlanner(), new CodeGenerator()) {
            @Override public GeneratedProject generateProjectFiles(ProjectSpecification spec, ProjectPlan plan) { return new GeneratedProject(spec.projectName(), "test", List.of(new GeneratedFile("../escape", "unsafe"))); }
        };
        var response = new AgentService(broken, validator).run("test");
        assertEquals("VALIDATION_FAILED", response.error().code()); assertTrue(response.files().isEmpty()); assertFalse(response.validation().valid());
    }
    @Test void unexpectedProviderExceptionIsSanitized() {
        var broken = new DemoLlmClient(new PromptAnalyzer(), new ProjectPlanner(), new CodeGenerator()) {
            @Override public ProjectSpecification analyzeRequirements(String prompt) { throw new RuntimeException("SECRET-DO-NOT-LEAK"); }
        };
        var response = new AgentService(broken, validator).run("test");
        assertFalse(StructuredJson.MAPPER.writeValueAsString(response).contains("SECRET-DO-NOT-LEAK"));
        assertEquals("EXECUTION_FAILED", response.error().code());
    }
    @Test void rejectsMalformedPackageAndInstallHooks() {
        for (String content : List.of("{invalid}", "{\"name\":\"test\",\"dependencies\":{\"react\":\"19\",\"react-dom\":\"19\"},\"scripts\":{\"postinstall\":\"echo blocked\"}}")) {
            var files = new CodeGenerator().generate("test").stream().map(f -> f.path().equals("package.json") ? new GeneratedFile(f.path(), content) : f).toList();
            assertFalse(validator.isValid(files));
        }
    }
}
