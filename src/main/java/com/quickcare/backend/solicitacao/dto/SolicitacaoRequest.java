package com.quickcare.backend.solicitacao.dto;

import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Usado tanto na criação quanto na edição. {@code endereco} é obrigatório
 * apenas quando a modalidade é PRESENCIAL — regra checada no service.
 */
public record SolicitacaoRequest(
        @NotNull Long especialidadeId,
        @NotBlank @Size(min = 20, max = 1000) String descricao,
        @NotNull ModalidadeSolicitacao modalidade,
        @Valid EnderecoSolicitacaoRequest endereco,
        @NotNull @FutureOrPresent LocalDate prazoDesejado
) {
}
