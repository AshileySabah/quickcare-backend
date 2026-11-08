package com.quickcare.backend.cadastro.dto;

import com.quickcare.backend.common.validation.MaiorDeIdade;
import com.quickcare.backend.usuario.Genero;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;
import java.util.List;

public record NovoPerfilPacienteRequest(
        @NotNull(message = "O preenchimento da data de nascimento é obrigatório.")
        @MaiorDeIdade(message = "O paciente deve ter pelo menos 18 anos.") LocalDate dataNascimento,
        @NotNull(message = "Escolha o gênero.") Genero genero,
        @Valid List<ContatoEmergenciaRequest> contatosEmergencia,
        @AssertTrue(message = "Confirme que as informações são verdadeiras.") boolean confirmacaoInformacoesVerdadeiras,
        @AssertTrue(message = "Para continuar, aceite o tratamento dos seus dados conforme a LGPD.") boolean consentimentoLgpd,
        String alergias,
        String condicoesSaude,
        String medicamentosEmUso,
        @NotBlank(message = "Informe o CEP.") @Pattern(regexp = "\\d{8}", message = "O CEP deve ter 8 dígitos.") String cep,
        @NotBlank(message = "Informe o logradouro.") String logradouro,
        @NotBlank(message = "Informe o número do endereço.") String numero,
        String complemento,
        @NotBlank(message = "Informe o bairro.") String bairro,
        @NotBlank(message = "Informe a cidade.") String cidade,
        @NotBlank(message = "Informe a UF do endereço.") @Pattern(regexp = "(?i)^[a-z]{2}$", message = "A UF deve ter 2 letras.") String uf
) {
}
