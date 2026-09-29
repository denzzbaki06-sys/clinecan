package com.clinecan.backend.controller;
import com.clinecan.backend.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;
@RestController
@RequestMapping("/api/preview")
public class PreviewController {
    private final ExecutionService service;
    public PreviewController(ExecutionService service) { this.service = service; }
    @GetMapping("/{id}/{token}/{*assetPath}")
    public ResponseEntity<byte[]> asset(@PathVariable String id, @PathVariable String token, @PathVariable String assetPath, HttpServletRequest request) {
        String host = request.getServerName();
        if (!Set.of("localhost", "127.0.0.1", "[::1]", "::1").contains(host) || !id.matches("[a-f0-9-]{36}") || !token.matches("[a-f0-9]{64}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String path = assetPath.startsWith("/") ? assetPath.substring(1) : assetPath;
        var asset = service.preview(id, token, path);
        String origin = request.getScheme() + "://" + (host.equals("::1") ? "[::1]" : host) + ":" + request.getServerPort();
        String scope = origin + "/api/preview/" + id + "/" + token + "/";
        // No allow-same-origin: scripts get an opaque origin even when this URL is opened directly.
        String policy = "sandbox allow-scripts; default-src 'none'; script-src " + scope + "; style-src " + scope + " 'unsafe-inline'; img-src " + scope + " data:; font-src " + scope + " data:; connect-src 'none'; worker-src 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'; form-action 'none'";
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.contentType()))
            .header("Content-Security-Policy", policy).header("X-Content-Type-Options", "nosniff")
            .header("Referrer-Policy", "no-referrer").header("Cache-Control", "no-store")
            .header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=(), usb=(), fullscreen=()")
            // ES modules inside an opaque-origin iframe require CORS; this allowance is preview-only.
            .header("Access-Control-Allow-Origin", "null").header("Vary", "Origin")
            .header("Cross-Origin-Resource-Policy", "cross-origin").body(asset.bytes());
    }
}
