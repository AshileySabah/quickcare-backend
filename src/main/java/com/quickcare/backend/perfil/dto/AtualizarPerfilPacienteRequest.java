package com.quickcare.backend.perfil.dto;

import com.quickcare.backend.cadastro.dto.ContatoEmergenciaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record AtualizarPerfilPacienteRequest(
        @NotBlank(message = "Informe o nome completo.") String nome,
        String telefone,
        @Valid List<ContatoEmergenciaRequest> contatosEmergencia,
        String alergias,
        String condicoesSaude,
        String medicamentosEmUso,
        @NotBlank(message = "Informe o CEP.") @Pattern(regexp = "\\d{8}", message = "O CEP deve ter 8 dígitos.") String cep,
        @NotBlank(message = "Informe o logradouro.") String logradouro,
        @NotBlank(message = "Informe o número do endereço.") String numero,
        String complemento,
        @NotBlank(message = "Informe o bairro.") String bairro,
        @NotBlank(message = "Informe a cidade.") String cidade,
        @NotBlank(message = "Informe a UF do endereço.") @Pattern(regexp = "(?i)^[a-z]{2}$", message = "A UF deve ter 2 letras.") String uf,
        boolean removerAvatar
) {
}
