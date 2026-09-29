package com.clinecan.backend.agent;

import com.clinecan.backend.model.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class OutputValidator {
    public static final int MAX_FILES = 32;
    public static final int MAX_FILE_CHARS = 150_000;
    public static final int MAX_PROJECT_CHARS = 1_000_000;
    public static final Set<String> REQUIRED = Set.of("src/App.tsx", "src/App.css", "src/main.tsx", "index.html", "package.json", "tsconfig.json", "vite.config.ts");

    /** Reject ambiguous forms instead of resolving traversal. Canonical output uses '/' only. */
    public boolean safePath(String path) {
        if (path == null || path.length() > 180 || !path.matches("[A-Za-z0-9_][A-Za-z0-9_./-]*") || path.endsWith("/")) return false;
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.startsWith(".") || segment.endsWith(".") || segment.length() > 80) return false;
            String lower = segment.toLowerCase(Locale.ROOT);
            if (Set.of("etc", "usr", "bin", "sbin", "dev", "proc", "sys", "root", "home", "users", "windows", "system32", "node_modules", "target", "id_rsa", "id_ed25519", "credentials", "secrets").contains(lower)
                || lower.matches("(con|prn|aux|nul|com[0-9]|lpt[0-9])(\\..*)?")
                || lower.endsWith(".pem") || lower.endsWith(".key") || lower.startsWith(".env")) return false;
        }
        return true;
    }

    public ValidationResult validateManifest(List<String> paths) {
        List<String> errors = new ArrayList<>();
        if (paths == null || paths.isEmpty() || paths.size() > MAX_FILES)
            return new ValidationResult(false, List.of("Manifest 1–32 dosya içermeli."), List.of());
        Set<String> seen = new HashSet<>();
        for (String path : paths) {
            if (!safePath(path)) errors.add("Güvensiz veya belirsiz dosya yolu reddedildi.");
            else if (!seen.add(path.toLowerCase(Locale.ROOT))) errors.add("Yinelenen dosya yolu reddedildi.");
        }
        for (String path : seen) {
            int slash = path.indexOf('/');
            while (slash >= 0) {
                if (seen.contains(path.substring(0, slash))) errors.add("Dosya ve klasör yolları çakışıyor.");
                slash = path.indexOf('/', slash + 1);
            }
        }
        if (!new HashSet<>(paths).containsAll(REQUIRED)) errors.add("React/Vite başlangıç dosyaları eksik.");
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors), List.of("Relative canonical paths", "Unique paths", "Required starter files"));
    }

    public ValidationResult validate(List<GeneratedFile> files, List<String> manifest) {
        if (files == null || files.isEmpty() || files.size() > MAX_FILES || files.stream().anyMatch(Objects::isNull))
            return new ValidationResult(false, List.of("Üretilen dosya listesi boş veya geçersiz."), List.of());
        var pathResult = validateManifest(files.stream().map(GeneratedFile::path).toList());
        List<String> errors = new ArrayList<>(pathResult.errors());
        long total = 0;
        for (var file : files) {
            if (file.content() == null || file.content().isBlank() || file.content().length() > MAX_FILE_CHARS || file.content().indexOf('\0') >= 0)
                errors.add("Dosya içeriği boş, çok büyük veya geçersiz.");
            else total += file.content().length();
        }
        if (total > MAX_PROJECT_CHARS) errors.add("Proje çıktı sınırını aşıyor.");
        if (manifest != null && (!validateManifest(manifest).valid() || !new HashSet<>(manifest).equals(new HashSet<>(files.stream().map(GeneratedFile::path).toList()))))
            errors.add("Üretilen dosyalar onaylanan manifest ile eşleşmiyor.");
        for (var file : files) {
            if ("package.json".equals(file.path()) || "tsconfig.json".equals(file.path())) {
                try {
                    var json = com.clinecan.backend.llm.StructuredJson.parse(file.content(), tools.jackson.databind.JsonNode.class);
                    if (!json.isObject()) errors.add("Proje JSON yapılandırması bir nesne olmalı.");
                    if ("package.json".equals(file.path())) {
                        if (!json.path("name").isString() || !json.path("dependencies").has("react") || !json.path("dependencies").has("react-dom"))
                            errors.add("React paket yapılandırması eksik.");
                        for (String hook : List.of("preinstall", "install", "postinstall", "prepare"))
                            if (json.path("scripts").has(hook)) errors.add("Otomatik kurulum kancalarına izin verilmiyor.");
                    }
                } catch (RuntimeException e) { errors.add("Proje JSON yapılandırması geçersiz."); }
            }
        }
        List<String> checks = new ArrayList<>(pathResult.checks());
        checks.add("JSON configuration and install-hook checks");
        checks.addAll(List.of("Bounded non-empty content", "Manifest agreement", "Static checks only; no build or code execution"));
        return new ValidationResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(checks));
    }
    public boolean isValid(List<GeneratedFile> files) { return validate(files, null).valid(); }
}
