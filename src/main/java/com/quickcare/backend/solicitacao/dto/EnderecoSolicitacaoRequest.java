package com.quickcare.backend.solicitacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EnderecoSolicitacaoRequest(
        @NotBlank(message = "Informe o CEP.") @Pattern(regexp = "\\d{8}", message = "O CEP deve ter 8 dígitos.") String cep,
        @NotBlank(message = "Informe o logradouro.")
        @Size(max = 150, message = "O logradouro deve ter no máximo 150 caracteres.") String logradouro,
        @NotBlank(message = "Informe o número do endereço.")
        @Size(max = 20, message = "O número do endereço deve ter no máximo 20 caracteres.") String numero,
        @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres.") String complemento,
        @NotBlank(message = "Informe o bairro.") @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres.") String bairro,
        @NotBlank(message = "Informe a cidade.") @Size(max = 100, message = "A cidade deve ter no máximo 100 caracteres.") String cidade,
        @NotBlank(message = "Informe a UF do endereço.") @Pattern(regexp = "(?i)^[a-z]{2}$", message = "A UF deve ter 2 letras.") String uf
) {
}
