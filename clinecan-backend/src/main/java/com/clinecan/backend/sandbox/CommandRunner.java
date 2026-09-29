package com.clinecan.backend.sandbox;
import java.util.List;
public interface CommandRunner {
    record Result(int exitCode, String stdout, String stderr, boolean timedOut, boolean truncated) {}
    Result run(List<String> command, int timeoutSeconds, int outputLimit) throws Exception;
}
