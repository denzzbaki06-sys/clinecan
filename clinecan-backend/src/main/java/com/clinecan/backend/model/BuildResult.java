package com.clinecan.backend.model;
public record BuildResult(boolean success, int exitCode, String stdout, String stderr, long durationMs, String failureCategory, boolean logsTruncated) {}
