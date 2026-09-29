package com.clinecan.backend.sandbox;
public final class Diagnostics {
    private Diagnostics() {}
    public static String safe(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("\\x1B\\[[0-?]*[ -/]*[@-~]", "")
            .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
            .replaceAll("(?i)(bearer\\s+)[^\\s]+", "$1[redacted]")
            .replaceAll("sk-[A-Za-z0-9_-]+", "[redacted]")
            .replaceAll("(?i)([a-z0-9_]*(?:key|token|secret|password)[a-z0-9_]*\\s*[:=]\\s*)[^\\s,;]+", "$1[redacted]")
            .replaceAll("/(?:Users|home)/[^\\s:]+", "[host-path]");
        return clean.substring(0, Math.min(8000, clean.length()));
    }
}
