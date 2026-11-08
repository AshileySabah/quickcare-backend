package com.quickcare.backend.cadastro.dto;

import jakarta.validation.constraints.NotBlank;

public record ContatoEmergenciaRequest(
        @NotBlank(message = "Informe o nome do contato de emergência.") String nome,
        @NotBlank(message = "Informe o telefone do contato de emergência.") String telefone
) {
}
