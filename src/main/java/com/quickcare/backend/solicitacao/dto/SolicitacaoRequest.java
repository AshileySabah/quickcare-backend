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
        @NotEmpty @Size(max = 10) List<@NotNull @Valid ProfissionalSolicitadoRequest> profissionais,
        @NotBlank @Size(min = 20, max = 1000) String descricao,
        @NotNull ModalidadeSolicitacao modalidade,
        @Valid EnderecoSolicitacaoRequest endereco,
        @NotNull @FutureOrPresent LocalDate prazoDesejado
) {
}
