package com.clinecan.backend.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bound JSON before MVC deserialization, including chunked requests. No body logging. */
@Component
public class RequestSizeFilter extends OncePerRequestFilter {
    private static final int MAX_BYTES = 65536;
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!"POST".equals(request.getMethod()) || !(request.getRequestURI().endsWith("/api/agent/run") || request.getRequestURI().endsWith("/api/agent/executions"))) { chain.doFilter(request, response); return; }
        if (request.getContentLengthLong() > MAX_BYTES) { reject(response); return; }
        byte[] body = request.getInputStream().readNBytes(MAX_BYTES + 1);
        if (body.length > MAX_BYTES) { reject(response); return; }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public int getContentLength() { return body.length; }
            @Override public long getContentLengthLong() { return body.length; }
            @Override public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public int read(byte[] bytes, int off, int len) { return input.read(bytes, off, len); }
                    public boolean isFinished() { return input.available() == 0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous endpoint"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
        }, response);
    }
    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":{\"code\":\"REQUEST_TOO_LARGE\",\"message\":\"İstek boyutu 64 KiB sınırını aşıyor.\",\"retryable\":false}}");
    }
}
