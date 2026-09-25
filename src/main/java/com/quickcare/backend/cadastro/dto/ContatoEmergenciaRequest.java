package com.quickcare.backend.cadastro.dto;

import jakarta.validation.constraints.NotBlank;

public record ContatoEmergenciaRequest(
        @NotBlank String nome,
        @NotBlank String telefone
) {
}
