package com.clinecan.backend.sandbox;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
/** Only trusted Docker CLI arguments enter this runner; never generated shell commands. */
public final class ProcessCommandRunner implements CommandRunner {
    public Result run(List<String> command, int timeoutSeconds, int outputLimit) throws Exception {
        var pb = new ProcessBuilder(command);
        var home = System.getenv("HOME"); var path = System.getenv("PATH");
        pb.environment().clear();
        if (home != null) pb.environment().put("HOME", home); // Docker client context only; not forwarded to container.
        if (path != null) pb.environment().put("PATH", path);
        Process process = pb.start();
        var readers = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var out = readers.submit(() -> capture(process.getInputStream(), outputLimit));
            var err = readers.submit(() -> capture(process.getErrorStream(), outputLimit));
            boolean ended;
            try { ended = process.waitFor(timeoutSeconds, TimeUnit.SECONDS); }
            finally { if (process.isAlive()) { process.destroyForcibly(); } }
            if (!ended) return new Result(-1, "", "Sandbox command timed out.", true, false);
            var stdout = out.get(5, TimeUnit.SECONDS); var stderr = err.get(5, TimeUnit.SECONDS);
            return new Result(ended ? process.exitValue() : -1, stdout.text(), stderr.text(), !ended, stdout.truncated() || stderr.truncated());
        } finally { if (process.isAlive()) process.destroyForcibly(); readers.shutdownNow(); }
    }
    record Captured(String text, boolean truncated) {}
    static Captured capture(InputStream stream, int limit) throws IOException {
        var bytes = new ByteArrayOutputStream(); byte[] chunk = new byte[4096]; boolean truncated = false; int n;
        while ((n = stream.read(chunk)) != -1) { int keep = Math.min(n, limit - bytes.size()); bytes.write(chunk, 0, keep); if (keep < n) truncated = true; }
        return new Captured(bytes.toString(StandardCharsets.UTF_8), truncated);
    }
}
