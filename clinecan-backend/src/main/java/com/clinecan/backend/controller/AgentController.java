package com.clinecan.backend.controller;

import com.clinecan.backend.model.AgentRequest;
import com.clinecan.backend.model.AgentResponse;
import com.clinecan.backend.service.AgentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "${clinecan.allowed-origins:http://localhost:5173,http://localhost:5174,https://clinecan.vercel.app}")
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/run")
    public ResponseEntity<AgentResponse> runAgent(
            @Valid @RequestBody AgentRequest request
    ) {

        AgentResponse response = agentService.run(request.prompt());

        return ResponseEntity.status("FAILED".equals(response.status()) ? 502 : 200).body(response);
    }
    @GetMapping("/capabilities")
    public java.util.Map<String, Object> capabilities() {
        return java.util.Map.of("generationMode", agentService.generationMode(), "streaming", true, "execution", "ISOLATED_BUILD", "legacyRun", "STATIC_ONLY");
    }
}