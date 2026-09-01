package com.quickcare.backend.cadastro.dto;

import com.quickcare.backend.profissional.ModalidadeAtendimento;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CadastroProfissionalRequest(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String senha,
        @NotBlank @Pattern(regexp = "\\d{11}") String cpf,
        String telefone,
        @NotNull ModalidadeAtendimento modalidadeAtendimento,
        @NotNull @Positive BigDecimal raioAtendimentoKm,
        @NotNull BigDecimal latitude,
        @NotNull BigDecimal longitude
) {
}
