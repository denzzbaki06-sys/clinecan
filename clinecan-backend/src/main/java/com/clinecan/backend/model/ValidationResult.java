package com.clinecan.backend.model;

public record ValidationResult(boolean valid, java.util.List<String> errors, java.util.List<String> checks) {}
