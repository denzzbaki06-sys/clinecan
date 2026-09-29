package com.clinecan.backend.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentRequest(
    @NotBlank(message = "Lütfen proje fikrini yaz.")
    @Size(max = 10000, message = "Proje fikri en fazla 10000 karakter olabilir.")
    String prompt
) {}
