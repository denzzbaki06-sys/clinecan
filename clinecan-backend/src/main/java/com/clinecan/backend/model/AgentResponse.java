package com.clinecan.backend.model;
import java.util.List;

public record AgentResponse(
    String projectName, String originalPrompt, String status, List<AgentStep> steps, List<GeneratedFile> files,
    String generationMode, String summary, ProjectSpecification specification, ProjectPlan plan,
    ValidationResult validation, AgentExecution execution, AgentError error
) {}
