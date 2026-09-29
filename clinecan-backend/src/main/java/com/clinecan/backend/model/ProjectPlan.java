package com.clinecan.backend.model;

public record ProjectPlan(java.util.List<ProjectTask> tasks, java.util.List<String> fileManifest) {}
