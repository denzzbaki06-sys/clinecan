package com.clinecan.backend.controller;
import com.clinecan.backend.service.ReadinessService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/agent/readiness")
@CrossOrigin(origins="${clinecan.allowed-origins:http://localhost:5173,http://localhost:5174}")
public class ReadinessController {
    private final ReadinessService readiness;
    public ReadinessController(ReadinessService readiness){this.readiness=readiness;}
    @GetMapping public ReadinessService.Status status(){return readiness.status();}
}
