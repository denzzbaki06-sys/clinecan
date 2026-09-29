package com.clinecan.backend.service;
import com.clinecan.backend.sandbox.*;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class ReadinessService {
    public record Status(String backend, String sandbox, String generationMode, boolean llmConfigured, String message) {}
    private final SandboxSettings settings; private final AgentService agent; private final CommandRunner runner;
    private long checked; private Status cached;
    @org.springframework.beans.factory.annotation.Autowired
    public ReadinessService(SandboxSettings settings, AgentService agent) { this(settings, agent, new ProcessCommandRunner()); }
    public ReadinessService(SandboxSettings settings, AgentService agent, CommandRunner runner) { this.settings=settings; this.agent=agent; this.runner=runner; }
    public synchronized Status status() {
        if(cached != null && System.currentTimeMillis()-checked < 10000) return cached;
        String sandbox="DISABLED";
        if(settings.enabled()) try {
            var daemon=runner.run(List.of("docker","version","--format","{{.Server.Version}}"),2,1000);
            sandbox=daemon.exitCode()==0 ? (runner.run(List.of("docker","image","inspect",settings.image(),"--format","{{.Id}}"),2,1000).exitCode()==0 ? "READY" : "IMAGE_MISSING") : "UNAVAILABLE";
        } catch(Exception ignored) { sandbox="UNAVAILABLE"; }
        cached=new Status("READY",sandbox,agent.generationMode(),"LLM".equals(agent.generationMode()),"READY".equals(sandbox)?"Yerel derleme hazır.":"Docker ve sandbox imajını kontrol et."); checked=System.currentTimeMillis(); return cached;
    }
}
