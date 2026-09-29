package com.clinecan.backend.llm;
import com.clinecan.backend.agent.*;
import com.clinecan.backend.model.*;

public class DemoLlmClient implements LlmClient {
    private final PromptAnalyzer analyzer;
    private final ProjectPlanner planner;
    private final CodeGenerator generator;
    public DemoLlmClient(PromptAnalyzer analyzer, ProjectPlanner planner, CodeGenerator generator) {
        this.analyzer = analyzer; this.planner = planner; this.generator = generator;
    }
    public String generationMode() { return "DEMO"; }
    public ProjectSpecification analyzeRequirements(String prompt) { return analyzer.specification(prompt); }
    public ProjectPlan createProjectPlan(ProjectSpecification specification) { return planner.plan(specification); }
    public GeneratedProject generateProjectFiles(ProjectSpecification specification, ProjectPlan plan) {
        return new GeneratedProject(specification.projectName(), "Deterministic demo: " + String.join(", ", specification.features()) + ". No LLM request was made.", generator.generate(specification));
    }
}
