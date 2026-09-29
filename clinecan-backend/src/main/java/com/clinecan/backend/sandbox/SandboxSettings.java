package com.clinecan.backend.sandbox;
public record SandboxSettings(boolean enabled, int timeoutSeconds, int memoryMb, double cpus, int concurrent, int repairAttempts, String image) {
    public SandboxSettings {
        if (timeoutSeconds < 1 || timeoutSeconds > 120 || memoryMb < 256 || memoryMb > 2048 || !Double.isFinite(cpus) || cpus < .25 || cpus > 2 || concurrent < 1 || concurrent > 4 || repairAttempts < 0 || repairAttempts > 2 || image == null || !image.matches("[a-zA-Z0-9][a-zA-Z0-9._/:-]{0,150}")) throw new IllegalArgumentException("Invalid sandbox limits");
    }
}
