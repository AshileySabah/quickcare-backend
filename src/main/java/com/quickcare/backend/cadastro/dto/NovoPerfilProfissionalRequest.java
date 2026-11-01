package com.quickcare.backend.cadastro.dto;

import com.quickcare.backend.common.validation.Cnpj;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.usuario.Genero;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Só os dados do perfil de profissional, para quem já tem conta (ver {@link NovoPerfilPacienteRequest}). */
public record NovoPerfilProfissionalRequest(
        @Pattern(regexp = "\\d{14}") @Cnpj String cnpj,
        @NotNull CategoriaProfissional categoria,
        @NotEmpty List<Long> especialidadeIds,
        String registroProfissional,
        @Pattern(regexp = "(?i)^[a-z]{2}$") String registroProfissionalUf,
        @NotNull @Past LocalDate dataNascimento,
        @NotNull Genero genero,
        @Valid List<ContatoEmergenciaRequest> contatosEmergencia,
        @AssertTrue boolean confirmacaoInformacoesVerdadeiras,
        @AssertTrue boolean consentimentoLgpd,
        boolean possuiSeguroResponsabilidadeCivil,
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
