package com.clinecan.backend.config;
import com.clinecan.backend.sandbox.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
@Configuration
public class SandboxConfiguration {
    @Bean public SandboxSettings sandboxSettings(Environment e) {
        return new SandboxSettings(e.getProperty("clinecan.sandbox.enabled", Boolean.class, true),
            e.getProperty("clinecan.sandbox.timeout-seconds", Integer.class, 90), e.getProperty("clinecan.sandbox.max-memory-mb", Integer.class, 512),
            e.getProperty("clinecan.sandbox.max-cpus", Double.class, 1.0), e.getProperty("clinecan.sandbox.max-concurrent", Integer.class, 2),
            e.getProperty("clinecan.repair.max-attempts", Integer.class, 2), e.getProperty("clinecan.sandbox.image", "clinecan-sandbox:phase3"));
    }
    @Bean public SandboxService sandboxService(SandboxSettings s) { return new DockerSandboxService(s, new ProcessCommandRunner()); }
}
