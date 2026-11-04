package com.quickcare.backend.perfil.dto;

import com.quickcare.backend.cadastro.dto.ContatoEmergenciaRequest;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record AtualizarPerfilProfissionalRequest(
        @NotBlank String nome,
        String telefone,
        @Valid List<ContatoEmergenciaRequest> contatosEmergencia,
        boolean possuiSeguroResponsabilidadeCivil,
        @NotNull ModalidadeAtendimento modalidadeAtendimento,
        @Positive BigDecimal raioAtendimentoKm,
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
