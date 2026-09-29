package com.clinecan.backend;
import com.clinecan.backend.agent.*;
import com.clinecan.backend.llm.*;
import com.clinecan.backend.model.*;
import com.clinecan.backend.sandbox.*;
import com.clinecan.backend.service.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class ExecutionTests {
    final SandboxSettings settings = new SandboxSettings(true,30,512,1,1,2,"image");
    DemoLlmClient demo() { return new DemoLlmClient(new PromptAnalyzer(), new ProjectPlanner(), new CodeGenerator()); }
    SandboxService.SandboxOutcome outcome(boolean success) { return new SandboxService.SandboxOutcome(new BuildResult(success, success ? 0 : 1, "src/App.tsx(1,1): error TS1005", "", 1, success ? null : "COMPILATION",false), success ? new byte[]{1,2} : null); }
    ExecutionService service(LlmClient llm, SandboxService sandbox) { return new ExecutionService(new AgentService(llm,new OutputValidator()),llm,sandbox,settings); }
    static ExecutionService.Snapshot await(ExecutionService service,String id) throws Exception {
        long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(System.nanoTime()<deadline) { var s=service.state(id); if(Set.of("COMPLETED","FAILED","CANCELLED","TIMED_OUT").contains(s.status())) return s; Thread.sleep(10); }
        throw new AssertionError("Execution did not terminate");
    }
    @Test void successEventsOrderedAndArtifactAvailable() throws Exception {
        var service=service(demo(), f -> outcome(true));
        try { var result=await(service,service.create("task").id()); assertEquals("COMPLETED",result.status()); assertTrue(result.preview().available()); assertTrue(result.build().success());
            for(int i=0;i<result.events().size();i++) { assertEquals(i+1,result.events().get(i).sequence()); assertEquals(result.id(),result.events().get(i).executionId()); }
            assertTrue(result.events().stream().anyMatch(e->e.stage().equals("BUILD") && e.status().equals("RUNNING")));
            assertEquals("FINISH",result.events().getLast().stage()); assertArrayEquals(new byte[]{1,2},service.artifact(result.id()));
        } finally { service.close(); }
    }
    @Test void demoFailureNeverFakesRepair() throws Exception {
        var calls=new AtomicInteger(); var service=service(demo(), f->{calls.incrementAndGet();return outcome(false);});
        try { var r=await(service,service.create("task").id()); assertEquals("FAILED",r.status());assertEquals(0,r.repairAttempts());assertEquals(1,calls.get());assertFalse(r.preview().available()); } finally {service.close();}
    }
    DemoLlmClient repairLlm(AtomicInteger repairs) { return new DemoLlmClient(new PromptAnalyzer(),new ProjectPlanner(),new CodeGenerator()) {
        @Override public String generationMode(){return "LLM";}
        @Override public RepairPatch repairProject(ProjectSpecification spec,ProjectPlan plan,List<GeneratedFile> files,String diagnostic){
            repairs.incrementAndGet(); assertTrue(diagnostic.contains("TS1005")); assertTrue(diagnostic.length()<=8000);
            var app=files.stream().filter(f->f.path().equals("src/App.tsx")).findFirst().orElseThrow();return new RepairPatch(List.of(new GeneratedFile(app.path(),app.content()+"\n// repaired")));
        }
    }; }
    @Test void failedBuildRepairValidatedRebuildSucceeds() throws Exception {
        var repairs=new AtomicInteger();var builds=new AtomicInteger();var service=service(repairLlm(repairs), f->outcome(builds.incrementAndGet()>1));
        try {var r=await(service,service.create("task").id());assertEquals("COMPLETED",r.status());assertEquals(1,repairs.get());assertEquals(2,builds.get());assertEquals(1,r.repairAttempts());assertTrue(r.result().files().stream().anyMatch(f->f.content().contains("// repaired")));assertTrue(r.events().stream().anyMatch(e->e.stage().equals("DIAGNOSE")));}finally{service.close();}
    }
    @Test void twoRepairsMaximumThenFailed() throws Exception {
        var repairs=new AtomicInteger();var builds=new AtomicInteger();var service=service(repairLlm(repairs), f->{builds.incrementAndGet();return outcome(false);});
        try {var r=await(service,service.create("task").id());assertEquals("FAILED",r.status());assertEquals(2,repairs.get());assertEquals(3,builds.get());assertFalse(r.preview().available());}finally{service.close();}
    }
    @Test void unsafePatchRejectedBeforeRebuild() {
        var spec=demo().analyzeRequirements("task");var plan=demo().createProjectPlan(spec);var files=demo().generateProjectFiles(spec,plan).files();
        for(var patch:List.of(new RepairPatch(List.of(new GeneratedFile("../bad","bad"))),new RepairPatch(List.of(files.getFirst(),files.getFirst())),new RepairPatch(List.of(new GeneratedFile("src/App.tsx","x".repeat(150001)))),new RepairPatch(List.of())))
            assertThrows(ProviderException.class,()->ExecutionService.merge(files,patch,plan));
    }
    @Test void capacityCancellationAndTimeout() throws Exception {
        var entered=new CountDownLatch(1);var blocker=new CountDownLatch(1);
        var service=service(demo(), f->{entered.countDown();try{blocker.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return outcome(false);});
        try {var first=service.create("task");assertTrue(entered.await(5,TimeUnit.SECONDS));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.create("task"));assertEquals("CANCELLED",service.cancel(first.id()).status());}finally{blocker.countDown();service.close();}
        var timed=service(demo(), f->new SandboxService.SandboxOutcome(new BuildResult(false,-1,"","",1,"BUILD_TIMEOUT",false),null));
        try{assertEquals("TIMED_OUT",await(timed,timed.create("task").id()).status());}finally{timed.close();}
    }
    @Test void noInternalSecretsInUnexpectedFailure() throws Exception {
        var service=service(demo(),f->{throw new IllegalStateException("PRIVATE-SECRET");});
        try{var r=await(service,service.create("task").id());assertEquals("FAILED",r.status());assertFalse(StructuredJson.MAPPER.writeValueAsString(r).contains("PRIVATE-SECRET"));}finally{service.close();}
    }
}
