package com.quickcare.backend.cadastro.dto;

import com.quickcare.backend.usuario.Genero;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;

/**
 * Só os dados do perfil de paciente. Usado por quem já tem conta e cria o
 * perfil logado: nome, e-mail, senha, CPF e identidade já estão no usuário.
 */
public record NovoPerfilPacienteRequest(
        @NotNull @Past LocalDate dataNascimento,
        @NotNull Genero genero,
        @Valid List<ContatoEmergenciaRequest> contatosEmergencia,
        @AssertTrue boolean confirmacaoInformacoesVerdadeiras,
        @AssertTrue boolean consentimentoLgpd,
        String alergias,
        String condicoesSaude,
        String medicamentosEmUso,
        @NotBlank @Pattern(regexp = "\\d{8}") String cep,
        @NotBlank String logradouro,
        @NotBlank String numero,
        String complemento,
        @NotBlank String bairro,
        @NotBlank String cidade,
        @NotBlank @Pattern(regexp = "(?i)^[a-z]{2}$") String uf
) {
}
