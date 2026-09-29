package com.clinecan.backend.controller;
import com.clinecan.backend.model.AgentRequest;
import com.clinecan.backend.service.ExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
@RestController
@RequestMapping("/api/agent/executions")
public class ExecutionController {
    private final ExecutionService service;
    public ExecutionController(ExecutionService service) { this.service = service; }
    @PostMapping public ResponseEntity<ExecutionService.Snapshot> create(@Valid @RequestBody AgentRequest request) {
        var result = service.create(request.prompt());
        return ResponseEntity.accepted().header("Location", "/api/agent/executions/" + result.id()).body(result);
    }
    @GetMapping("/{id}") public ExecutionService.Snapshot state(@PathVariable String id) { return service.state(id); }
    @DeleteMapping("/{id}") public ExecutionService.Snapshot cancel(@PathVariable String id) { return service.cancel(id); }
    @GetMapping(value="/{id}/events", produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String id, @RequestHeader(value="Last-Event-ID", defaultValue="0") long after) { return service.subscribe(id, after); }
    @GetMapping("/{id}/artifact") public ResponseEntity<byte[]> artifact(@PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header("Content-Disposition", "attachment; filename=clinecan-build.zip").header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "sandbox; default-src 'none'").header("Cache-Control", "no-store").body(service.artifact(id));
    }
}
