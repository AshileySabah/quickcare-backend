package com.quickcare.backend.perfil.dto;

import com.quickcare.backend.cadastro.dto.ContatoEmergenciaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record AtualizarPerfilPacienteRequest(
        @NotBlank String nome,
        String telefone,
        @Valid List<ContatoEmergenciaRequest> contatosEmergencia,
        String alergias,
        String condicoesSaude,
        String medicamentosEmUso,
        @NotBlank @Pattern(regexp = "\\d{8}") String cep,
        @NotBlank String logradouro,
        @NotBlank String numero,
        String complemento,
        @NotBlank String bairro,
        @NotBlank String cidade,
        @NotBlank @Pattern(regexp = "(?i)^[a-z]{2}$") String uf,
        boolean removerAvatar
) {
}
