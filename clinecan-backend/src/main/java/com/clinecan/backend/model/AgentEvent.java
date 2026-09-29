package com.clinecan.backend.model;

public record AgentEvent(String timestamp, String stage, String message, String status) {}
