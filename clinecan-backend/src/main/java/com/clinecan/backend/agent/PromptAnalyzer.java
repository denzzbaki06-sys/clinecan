package com.clinecan.backend.agent;

import org.springframework.stereotype.Component;

@Component
public class PromptAnalyzer {

    public String analyze(String prompt) {

        String normalizedPrompt = prompt.trim().toLowerCase(java.util.Locale.ROOT);

        if (normalizedPrompt.contains("expense")
                || normalizedPrompt.contains("finance")
                || normalizedPrompt.contains("harcama")) {

            return "Personal finance application detected.";
        }

        if (normalizedPrompt.contains("todo")
                || normalizedPrompt.contains("task")
                || normalizedPrompt.contains("görev")) {

            return "Task management application detected.";
        }

        if (normalizedPrompt.contains("portfolio")) {
            return "Portfolio website detected.";
        }

        return "General web application detected.";
    }
    public com.clinecan.backend.model.ProjectSpecification specification(String prompt) {
        String kind = analyze(prompt);
        String name;
        java.util.List<String> features;
        if (kind.contains("finance")) { name = "expense-tracker"; features = java.util.List.of("Expense entry", "Category totals", "Monthly analytics", "Category charts"); }
        else if (kind.contains("Task")) { name = "task-manager"; features = java.util.List.of("Create tasks", "Priority selection", "Status filters", "Task deadlines"); }
        else if (kind.contains("Portfolio")) { name = "portfolio-website"; features = java.util.List.of("Projects section", "Skills section", "Contact section"); }
        else { name = "clinecan-generated-app"; features = java.util.List.of("Application shell", "Project overview"); }
        return new com.clinecan.backend.model.ProjectSpecification(name, prompt.substring(0, Math.min(2800, prompt.length())), "WEB_APP", features, "React + TypeScript", false);
    }
}