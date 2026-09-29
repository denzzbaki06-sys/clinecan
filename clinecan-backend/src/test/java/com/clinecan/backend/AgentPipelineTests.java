package com.clinecan.backend;

import com.clinecan.backend.agent.*;
import com.clinecan.backend.model.*;
import com.clinecan.backend.service.AgentService;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AgentPipelineTests {
    @Test void generatesCompleteStarterAndOrderedSteps() {
        var service = new AgentService(new PromptAnalyzer(), new ProjectPlanner(), new CodeGenerator(), new OutputValidator());
        var result = service.run("Create a personal expense tracking dashboard");
        assertEquals("COMPLETED", result.status());
        assertEquals(List.of("ANALYZE", "PLAN", "GENERATE", "VALIDATE"), result.steps().stream().map(AgentStep::name).toList());
        assertEquals(7, result.files().size());
        assertTrue(new OutputValidator().isValid(result.files()));
    }
    @Test void escapesJsxInjection() {
        String content = new CodeGenerator().generate("<script>{alert('x')}</script> &").getFirst().content();
        assertTrue(content.contains("&lt;script&gt;&#123;"));
        assertFalse(content.contains("<script>"));
    }
    @Test void rejectsIncompleteAndUnsafeOutput() {
        var validator = new OutputValidator();
        assertFalse(validator.isValid(List.of()));
        assertFalse(validator.isValid(List.of(new GeneratedFile("../bad", "x"))));
        var files = new java.util.ArrayList<>(new CodeGenerator().generate("test"));
        files.add(files.getFirst());
        assertFalse(validator.isValid(files));
    }
}
