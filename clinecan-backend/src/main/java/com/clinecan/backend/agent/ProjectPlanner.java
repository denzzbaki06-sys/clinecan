package com.clinecan.backend.agent;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProjectPlanner {

    public List<String> createPlan(String prompt) {

        return List.of(
                "Analyze user requirements",
                "Design project structure",
                "Generate application files",
                "Generate UI components",
                "Validate generated project"
        );
    }
    public com.clinecan.backend.model.ProjectPlan plan(com.clinecan.backend.model.ProjectSpecification specification) {
        var tasks = new java.util.ArrayList<com.clinecan.backend.model.ProjectTask>();
        tasks.add(new com.clinecan.backend.model.ProjectTask("shell", "Create React/Vite application shell", "FILE_GENERATION"));
        for (int i = 0; i < specification.features().size(); i++) {
            tasks.add(new com.clinecan.backend.model.ProjectTask("feature-" + i, specification.features().get(i), "COMPONENT_GENERATION"));
        }
        tasks.add(new com.clinecan.backend.model.ProjectTask("validate", "Validate file manifest and content limits", "VALIDATION"));
        return new com.clinecan.backend.model.ProjectPlan(tasks, OutputValidator.REQUIRED.stream().sorted().toList());
    }
}