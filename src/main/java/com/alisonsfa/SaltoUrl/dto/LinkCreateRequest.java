package com.alisonsfa.SaltoUrl.dto;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LinkCreateRequest(
    @NotBlank(message = "O campo URL não pode estar vazio.")
    @URL(message = "O campo URL deve ser uma URL válida.")
    @Pattern(regexp = "^https?://.*", message = "Apenas protocolos HTTP e HTTPS são permitidos.")
    String originalUrl
) {}
