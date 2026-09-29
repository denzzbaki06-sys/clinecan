package com.clinecan.backend.model;

public record AgentError(String code, String message, boolean retryable) {}
