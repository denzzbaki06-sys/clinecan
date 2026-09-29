package com.clinecan.backend.sandbox;
import com.clinecan.backend.agent.OutputValidator;
import com.clinecan.backend.llm.*;
import com.clinecan.backend.model.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.zip.*;
import tools.jackson.databind.JsonNode;
public final class DockerSandboxService implements SandboxService {
    private final SandboxSettings settings; private final CommandRunner runner; private final Semaphore permits;
    public DockerSandboxService(SandboxSettings settings, CommandRunner runner) { this.settings = settings; this.runner = runner; permits = new Semaphore(settings.concurrent()); cleanupStale(); }
    public List<String> launchCommand(String name, Path input) {
        return List.of("docker", "run", "--detach", "--rm", "--pull=never", "--name", name,
            "--label", "clinecan.sandbox=phase3", "--network=none", "--read-only", "--user=1000:1000",
            "--cap-drop=ALL", "--security-opt=no-new-privileges", "--pids-limit=128",
            "--memory=" + settings.memoryMb() + "m", "--memory-swap=" + settings.memoryMb() + "m", "--cpus=" + settings.cpus(),
            "--log-driver=none", "--ulimit=nofile=256:256",
            "--tmpfs=/workspace:rw,nosuid,nodev,size=256m,uid=1000,gid=1000", "--tmpfs=/tmp:rw,nosuid,nodev,size=64m,uid=1000,gid=1000",
            "--mount", "type=bind,source=" + input.toAbsolutePath() + ",target=/input,readonly", settings.image(), "sleep", "600");
    }
    public SandboxOutcome build(List<GeneratedFile> files) {
        long start = System.nanoTime();
        if (!settings.enabled()) return failure("SANDBOX_DISABLED", start);
        if (!permits.tryAcquire()) return failure("SANDBOX_CAPACITY", start);
        Path input = null; String name = "clinecan-" + UUID.randomUUID();
        try {
            if (!new OutputValidator().isValid(files)) return failure("UNSAFE_MANIFEST", start);
            new DependencyPolicy().validate(files);
            input = Files.createTempDirectory("clinecan-sandbox-");
            Files.setPosixFilePermissions(input, PosixFilePermissions.fromString("rwxr-xr-x"));
            for (var file : files) {
                Path destination = input.resolve(file.path()).normalize();
                if (!destination.startsWith(input)) return failure("UNSAFE_MANIFEST", start);
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, file.content(), StandardOpenOption.CREATE_NEW);
            }
            var launched = runner.run(launchCommand(name, input), 15, 16000);
            if (launched.exitCode() != 0) return failure("SANDBOX_UNAVAILABLE", start);
            var compiled = runner.run(List.of("docker", "exec", name, "sh", "/opt/toolchain/build.sh"), settings.timeoutSeconds(), 16000);
            String category = compiled.timedOut() ? "BUILD_TIMEOUT" : compiled.exitCode() == 0 ? null : "COMPILATION";
            var result = new BuildResult(category == null, compiled.exitCode(), Diagnostics.safe(compiled.stdout()), Diagnostics.safe(compiled.stderr()), elapsed(start), category, compiled.truncated());
            if (!result.success()) return new SandboxOutcome(result, null);
            var exported = runner.run(List.of("docker", "exec", name, "node", "/opt/toolchain/export.mjs"), 10, 3_000_000);
            if (exported.exitCode() != 0 || exported.truncated()) return failure("ARTIFACT_INVALID", start);
            return new SandboxOutcome(result, archive(exported.stdout()));
        } catch (ProviderException e) { return failure(e.error().code(), start); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return failure("CANCELLED", start); }
        catch (Exception e) { return failure("SANDBOX_UNAVAILABLE", start); }
        finally {
            boolean interrupted = Thread.interrupted();
            try { runner.run(List.of("docker", "rm", "--force", name), 10, 1000); } catch (Exception ignored) { /* Container also self-expires after 600s. */ }
            if (input != null) try (var paths = Files.walk(input)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }); } catch (IOException ignored) {}
            permits.release(); if (interrupted) Thread.currentThread().interrupt();
        }
    }
    static void cleanupStale() {
        Path root = Path.of(System.getProperty("java.io.tmpdir"));
        try (var directories = Files.newDirectoryStream(root, "clinecan-sandbox-*")) {
            for (var dir : directories) {
                if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) || Files.getLastModifiedTime(dir, LinkOption.NOFOLLOW_LINKS).toMillis() > System.currentTimeMillis() - 1_800_000) continue;
                try (var paths = Files.walk(dir)) { paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }); }
            }
        } catch (IOException ignored) { /* No secret or generated content is logged. */ }
    }
    private byte[] archive(String json) throws IOException {
        // This is bounded build data from a networkless container, never provider text or host paths.
        JsonNode root = StructuredJson.MAPPER.readTree(json); var files = root.path("files");
        if (!files.isArray() || files.isEmpty() || files.size() > 64) throw new IOException();
        var out = new ByteArrayOutputStream(); var seen = new HashSet<String>(); int total = 0;
        try (var zip = new ZipOutputStream(out)) {
            for (var file : files) {
                String path = file.path("path").asText();
                if (!new OutputValidator().safePath(path) || !seen.add(path.toLowerCase(Locale.ROOT)) || !file.path("content").isString()) throw new IOException();
                byte[] bytes = Base64.getDecoder().decode(file.path("content").asText()); total += bytes.length;
                if (bytes.length > 1_000_000 || total > 2_000_000) throw new IOException();
                zip.putNextEntry(new ZipEntry(path)); zip.write(bytes); zip.closeEntry();
            }
        }
        if (!seen.contains("index.html")) throw new IOException();
        return out.toByteArray();
    }
    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
    private static SandboxOutcome failure(String category, long start) { return new SandboxOutcome(new BuildResult(false, -1, "", "Derleme tamamlanamadı: " + category, elapsed(start), category, false), null); }
}
