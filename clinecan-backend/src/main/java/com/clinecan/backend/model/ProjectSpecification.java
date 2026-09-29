package com.clinecan.backend.model;

public record ProjectSpecification(String projectName, String description, String applicationType, java.util.List<String> features, String frontend, boolean backendRequired) {}
