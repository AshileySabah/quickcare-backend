package com.quickcare.backend.cadastro.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CadastroPacienteRequest(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String senha,
        @NotBlank @Pattern(regexp = "\\d{11}") String cpf,
        String telefone
) {
}
