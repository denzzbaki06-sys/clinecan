package com.clinecan.backend.sandbox;
import com.clinecan.backend.model.*;
import com.clinecan.backend.llm.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
public final class DependencyPolicy {
    private static final Set<String> ALLOWED = Set.of("react", "react-dom", "typescript", "vite", "@types/react", "@types/react-dom", "@vitejs/plugin-react");
    public void validate(List<GeneratedFile> files) {
        var packageFile = files.stream().filter(f -> "package.json".equals(f.path())).findFirst().orElseThrow(ProviderException::malformed);
        var p = StructuredJson.parse(packageFile.content(), JsonNode.class);
        for (String field : List.of("workspaces", "overrides", "resolutions", "optionalDependencies", "peerDependencies", "bundledDependencies", "bundleDependencies", "packageManager"))
            if (p.has(field)) reject();
        for (String section : List.of("dependencies", "devDependencies")) {
            var deps = p.path(section);
            if (deps.isMissingNode()) continue;
            if (!deps.isObject()) reject();
            deps.properties().forEach(entry -> {
                if (!ALLOWED.contains(entry.getKey()) || !entry.getValue().isString() || !entry.getValue().asText().matches("[~^]?[0-9]+\\.[0-9]+\\.[0-9]+")) reject();
            });
        }
        var scripts = p.path("scripts");
        if (!scripts.isMissingNode()) {
            if (!scripts.isObject()) reject();
            scripts.properties().forEach(entry -> {
                var allowed = Map.of("dev", Set.of("vite"), "preview", Set.of("vite preview"), "build", Set.of("vite build", "tsc --noEmit && vite build", "tsc -b && vite build"));
                if (!allowed.containsKey(entry.getKey()) || !entry.getValue().isString() || !allowed.get(entry.getKey()).contains(entry.getValue().asText())) reject();
            });
        }
        for (var f : files) if (f.path().equals("node_modules") || f.path().startsWith("node_modules/") || f.path().equals("tsconfig.check.json") || f.path().equals("dist") || f.path().startsWith("dist/")) reject();
    }
    private static void reject() { throw new ProviderException("DEPENDENCY_POLICY", "Proje kontrollü React/Vite bağımlılık ve komut politikasına uymuyor.", false); }
}
