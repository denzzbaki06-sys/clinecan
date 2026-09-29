package com.clinecan.backend.llm;
import com.clinecan.backend.model.*;

/** Provider-independent, typed boundaries. Returned files are data only. */
public interface LlmClient {
    String generationMode();
    ProjectSpecification analyzeRequirements(String prompt);
    ProjectPlan createProjectPlan(ProjectSpecification specification);
    GeneratedProject generateProjectFiles(ProjectSpecification specification, ProjectPlan plan);
    default GeneratedProject generateProjectFiles(String originalPrompt, ProjectSpecification specification, ProjectPlan plan) {
        return generateProjectFiles(specification, plan);
    }
    default RepairPatch repairProject(ProjectSpecification specification, ProjectPlan plan, java.util.List<GeneratedFile> files, String diagnostics) {
        throw new ProviderException("REPAIR_UNAVAILABLE", "DEMO modu AI onarımı yapmaz.", false);
    }
}
