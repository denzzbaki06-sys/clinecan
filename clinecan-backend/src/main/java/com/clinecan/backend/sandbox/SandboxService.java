package com.clinecan.backend.sandbox;
import com.clinecan.backend.model.*;
import java.util.List;
public interface SandboxService {
    SandboxOutcome build(List<GeneratedFile> files);
    record SandboxOutcome(BuildResult build, byte[] artifact) {}
}
