package com.clinecan.backend.model;

public record AgentStep(String name, String status, String message, long durationMs, AgentError error) {
    public AgentStep(String name, String status, String message) { this(name, status, message, 0, null); }
}
