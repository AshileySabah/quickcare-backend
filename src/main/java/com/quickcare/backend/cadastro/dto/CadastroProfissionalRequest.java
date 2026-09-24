package com.quickcare.backend.cadastro.dto;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CadastroProfissionalRequest(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String senha,
        @NotBlank @Pattern(regexp = "\\d{11}") String cpf,
        String telefone,
        @Pattern(regexp = "\\d{14}") String cnpj,
        @NotNull CategoriaProfissional categoria,
        @NotEmpty List<Long> especialidadeIds,
        String registroProfissional,
        @NotNull ModalidadeAtendimento modalidadeAtendimento,
        @Positive BigDecimal raioAtendimentoKm,
        @NotBlank @Pattern(regexp = "\\d{8}") String cep,
        @NotBlank String logradouro,
        @NotBlank String numero,
        String complemento,
        @NotBlank String bairro,
        @NotBlank String cidade,
        @NotBlank @Pattern(regexp = "(?i)^[a-z]{2}$") String uf
) {
}
