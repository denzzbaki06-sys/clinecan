package com.clinecan.backend.service;
import com.clinecan.backend.agent.OutputValidator;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
/** Bounded ZIP inspection entirely in memory; never extracts generated files onto the host. */
public final class PreviewAssets {
    private PreviewAssets() {}
    public record Asset(byte[] bytes, String contentType) {}
    public static Map<String, Asset> read(byte[] zipBytes) {
        var result = new LinkedHashMap<String, Asset>(); var seen = new HashSet<String>(); int total = 0;
        if (zipBytes == null || zipBytes.length > 2_100_000) throw invalid();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String path = entry.getName();
                if (entry.isDirectory() || !safe(path) || !seen.add(path.toLowerCase(Locale.ROOT)) || result.size() >= 64) throw invalid();
                byte[] bytes = zip.readNBytes(1_000_001); total += bytes.length;
                if (bytes.length > 1_000_000 || total > 2_000_000) throw invalid();
                result.put(path, new Asset(bytes, type(path)));
            }
        } catch (IOException e) { throw invalid(); }
        if (!result.containsKey("index.html")) throw invalid();
        return Map.copyOf(result);
    }
    public static boolean safe(String path) { return path != null && !path.contains("..") && new OutputValidator().safePath(path); }
    public static String type(String path) {
        String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return switch(extension) {
            case "html" -> "text/html;charset=UTF-8";
            case "js", "mjs" -> "text/javascript;charset=UTF-8";
            case "css" -> "text/css;charset=UTF-8";
            case "json" -> "application/json";
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "ico" -> "image/x-icon";
            case "woff" -> "font/woff";
            case "woff2" -> "font/woff2";
            default -> "application/octet-stream";
        };
    }
    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Preview unavailable"); }
}
