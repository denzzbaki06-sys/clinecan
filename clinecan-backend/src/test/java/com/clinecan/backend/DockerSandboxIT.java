package com.clinecan.backend;
import com.clinecan.backend.agent.*;
import com.clinecan.backend.llm.*;
import com.clinecan.backend.model.*;
import com.clinecan.backend.sandbox.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Explicit opt-in: ./mvnw -Dtest=DockerSandboxIT test. No OpenAI calls. */
class DockerSandboxIT {
    List<GeneratedFile> files() {return new CodeGenerator().generate(new PromptAnalyzer().specification("Create a modern task management application with priorities and status filters."));}
    DockerSandboxService sandbox(){return new DockerSandboxService(new SandboxSettings(true,90,512,1,1,2,"clinecan-sandbox:phase3"),new ProcessCommandRunner());}
    @Test void realDemoTaskBuildAndArtifact() {
        var result=sandbox().build(files()); assertTrue(result.build().success(),result.build().toString());assertNotNull(result.artifact());assertTrue(result.artifact().length>1000);
    }
    @Test void realTypeScriptFailure() {
        var broken=files().stream().map(f->f.path().equals("src/App.tsx")?new GeneratedFile(f.path(),"const n: number = 'broken'; export default function App(){ return <p>{n}</p> }"):f).toList();
        var result=sandbox().build(broken);assertFalse(result.build().success());assertEquals("COMPILATION",result.build().failureCategory());assertTrue(result.build().stdout().contains("TS2322"));assertNull(result.artifact());
    }

    @Test void realContainerTimeoutAndRemoval() {
        var runner = new ProcessCommandRunner(); var container = new java.util.concurrent.atomic.AtomicReference<String>();
        CommandRunner delayed = (command,timeout,limit) -> {
            if (command.contains("sh")) {
                container.set(command.get(2));
                return runner.run(List.of("docker","exec",command.get(2),"node","-e","setInterval(() => {}, 1000)"),1,limit);
            }
            return runner.run(command,timeout,limit);
        };
        var result = new DockerSandboxService(new SandboxSettings(true,1,512,1,1,2,"clinecan-sandbox:phase3"),delayed).build(files());
        assertEquals("BUILD_TIMEOUT",result.build().failureCategory());
        try { assertNotEquals(0,runner.run(List.of("docker","inspect",container.get()),5,1000).exitCode()); } catch(Exception e) { throw new AssertionError(e); }
    }
    @Test void realBuildRepairWithStubLlmAndVerifiedIsolation() throws Exception {
        var runner = new ProcessCommandRunner(); var inspections = new java.util.concurrent.atomic.AtomicInteger();
        CommandRunner inspected = (command,timeout,limit) -> {
            if(command.contains("sh")) {
                var config = StructuredJson.MAPPER.readTree(runner.run(List.of("docker","inspect",command.get(2)),5,32000).stdout()).path(0);
                assertTrue(config.path("HostConfig").path("ReadonlyRootfs").asBoolean());
                assertEquals("none",config.path("HostConfig").path("NetworkMode").asText());
                assertEquals(536870912L,config.path("HostConfig").path("Memory").asLong());
                assertEquals("1000:1000",config.path("Config").path("User").asText());
                assertFalse(config.path("HostConfig").path("Privileged").asBoolean());
                assertFalse(config.path("Config").path("Env").toString().contains("CLINECAN_LLM"));
                for(var mount:config.path("Mounts")) if("bind".equals(mount.path("Type").asText())) { assertEquals("/input",mount.path("Destination").asText()); assertFalse(mount.path("RW").asBoolean()); }
                inspections.incrementAndGet();
            }
            return runner.run(command,timeout,limit);
        };
        var llm = new DemoLlmClient(new PromptAnalyzer(),new ProjectPlanner(),new CodeGenerator()) {
            @Override public String generationMode(){return "LLM";}
            @Override public GeneratedProject generateProjectFiles(ProjectSpecification spec,ProjectPlan plan){
                var project=super.generateProjectFiles(spec,plan);
                return new GeneratedProject(project.projectName(),project.summary(),project.files().stream().map(f->f.path().equals("src/App.tsx")?new GeneratedFile(f.path(),"const n: number = 'broken'; export default function App(){return <p>{n}</p>}"):f).toList());
            }
            @Override public RepairPatch repairProject(ProjectSpecification spec,ProjectPlan plan,List<GeneratedFile> files,String diagnostics){
                assertTrue(diagnostics.contains("TS2322"));return new RepairPatch(List.of(new GeneratedFile("src/App.tsx","export default function App(){return <main>Repaired task app</main>}")));
            }
        };
        var limits = new SandboxSettings(true,90,512,1,1,2,"clinecan-sandbox:phase3");
        var service=new com.clinecan.backend.service.ExecutionService(new com.clinecan.backend.service.AgentService(llm,new OutputValidator()),llm,new DockerSandboxService(limits,inspected),limits);
        try { var result=ExecutionTests.await(service,service.create("task manager").id());assertEquals("COMPLETED",result.status(),String.valueOf(result.build()));assertEquals(1,result.repairAttempts());assertEquals(2,inspections.get());assertTrue(result.build().success()); } finally {service.close();}
    }
}
