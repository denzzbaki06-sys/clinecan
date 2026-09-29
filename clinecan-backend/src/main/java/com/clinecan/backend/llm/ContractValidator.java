package com.clinecan.backend.llm;
import com.clinecan.backend.model.*;
import com.clinecan.backend.agent.OutputValidator;
import java.util.*;

public final class ContractValidator {
    private ContractValidator() {}
    private static boolean text(String s, int max) { return s != null && !s.isBlank() && s.length() <= max; }
    public static ProjectSpecification specification(ProjectSpecification s) {
        if (s == null || s.projectName() == null || !s.projectName().matches("[a-z][a-z0-9-]{0,59}")
            || !text(s.description(), 3000) || !"WEB_APP".equals(s.applicationType()) || !"React + TypeScript".equals(s.frontend())
            || s.features() == null || s.features().isEmpty() || s.features().size() > 20 || s.features().stream().anyMatch(f -> !text(f, 300))) throw ProviderException.malformed();
        // Phase 2 deliberately supports frontend-only projects; never claim to have built a server.
        if (s.backendRequired()) throw new ProviderException("UNSUPPORTED_SCOPE", "Bu aşamada yalnızca frontend projeleri destekleniyor. Backend gereksinimini sonraki aşamaya ayır.", false);
        return s;
    }
    public static ProjectPlan plan(ProjectPlan p) {
        if (p == null || p.tasks() == null || p.tasks().isEmpty() || p.tasks().size() > 24) throw ProviderException.malformed();
        Set<String> ids = new HashSet<>();
        for (var task : p.tasks()) {
            if (task == null || !text(task.id(), 60) || !ids.add(task.id()) || !text(task.title(), 300)
                || !Set.of("FILE_GENERATION", "COMPONENT_GENERATION", "VALIDATION").contains(task.type() == null ? "" : task.type())) throw ProviderException.malformed();
        }
        if (!new OutputValidator().validateManifest(p.fileManifest()).valid()) throw new ProviderException("UNSAFE_MANIFEST", "Dosya manifesti güvenlik veya yapı kontrolünden geçemedi.", false);
        return p;
    }
    public static GeneratedProject project(GeneratedProject p, String expectedName) {
        if (p == null || !expectedName.equals(p.projectName()) || !text(p.summary(), 3000) || p.files() == null) throw ProviderException.malformed();
        if (p.files().size() > OutputValidator.MAX_FILES) throw new ProviderException("OUTPUT_TOO_LARGE", "Üretilen proje en fazla 32 dosya içerebilir.", false);
        return p;
    }
}
