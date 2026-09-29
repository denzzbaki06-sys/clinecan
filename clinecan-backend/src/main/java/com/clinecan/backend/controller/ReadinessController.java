package com.clinecan.backend.controller;
import com.clinecan.backend.service.ReadinessService;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/agent/readiness")
public class ReadinessController {
    private final ReadinessService readiness;
    public ReadinessController(ReadinessService readiness){this.readiness=readiness;}
    @GetMapping public ReadinessService.Status status(){return readiness.status();}
}
