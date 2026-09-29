package com.clinecan.backend.controller;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<?> invalidRequest(Exception ignored) {
        return ResponseEntity.badRequest().body(Map.of("error", Map.of("code", "INVALID_REQUEST", "message", "Geçerli bir JSON isteğinde 1–10000 karakterlik prompt gönder.", "retryable", false)));
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<?> status(org.springframework.web.server.ResponseStatusException failure) {
        return ResponseEntity.status(failure.getStatusCode()).body(Map.of("error", Map.of("code", "EXECUTION_REQUEST", "message", "Yürütme bulunamadı veya kapasite sınırına ulaşıldı.", "retryable", failure.getStatusCode().value() == 429)));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception ignored) {
        return ResponseEntity.internalServerError().body(Map.of("error", Map.of("code", "INTERNAL_ERROR", "message", "İstek tamamlanamadı.", "retryable", false)));
    }
}
