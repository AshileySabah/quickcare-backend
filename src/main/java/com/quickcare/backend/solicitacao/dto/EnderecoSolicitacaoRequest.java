package com.quickcare.backend.solicitacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnderecoSolicitacaoRequest(
        @NotBlank @Pattern(regexp = "\\d{8}") String cep,
        @NotBlank @Size(max = 150) String logradouro,
        @NotBlank @Size(max = 20) String numero,
        @Size(max = 100) String complemento,
        @NotBlank @Size(max = 100) String bairro,
        @NotBlank @Size(max = 100) String cidade,
        @NotBlank @Pattern(regexp = "(?i)^[a-z]{2}$") String uf
) {
}
