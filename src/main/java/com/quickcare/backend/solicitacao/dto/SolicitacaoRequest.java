package com.quickcare.backend.solicitacao.dto;

import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record SolicitacaoRequest(
        @NotEmpty(message = "Inclua pelo menos um profissional na solicitação.")
        @Size(max = 10, message = "A solicitação pode pedir no máximo 10 tipos de profissional.")
        List<@NotNull(message = "Inclua pelo menos um profissional na solicitação.") @Valid ProfissionalSolicitadoRequest> profissionais,
        @NotBlank(message = "Descreva o que você precisa.")
        @Size(min = 20, max = 1000, message = "A descrição deve ter entre 20 e 1000 caracteres.") String descricao,
        @NotNull(message = "Escolha se o atendimento é presencial ou online.") ModalidadeSolicitacao modalidade,
        @Valid EnderecoSolicitacaoRequest endereco,
        @NotNull(message = "Informe o prazo desejado.")
        @FutureOrPresent(message = "O prazo desejado não pode ser uma data que já passou.") LocalDate prazoDesejado
) {
}
