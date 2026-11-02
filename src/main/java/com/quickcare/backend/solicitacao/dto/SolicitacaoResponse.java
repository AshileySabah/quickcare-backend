package com.quickcare.backend.solicitacao.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.StatusSolicitacao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public record SolicitacaoResponse(
        Long id,
        Long pacienteId,
        List<ProfissionalSolicitadoResponse> profissionais,
        String descricao,
        ModalidadeSolicitacao modalidade,
        EnderecoSolicitacaoResponse endereco,
        LocalDate prazoDesejado,
        StatusSolicitacao status,
        Instant criadoEm,
        Instant atualizadoEm,
        long totalPropostas,
        boolean atendimentoEmAndamento,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant proximoAtendimentoInicio
) {

    public static SolicitacaoResponse de(Solicitacao solicitacao, long totalPropostas, List<Atendimento> ativos) {
        EnderecoSolicitacaoResponse endereco = solicitacao.getModalidade() == ModalidadeSolicitacao.PRESENCIAL
                ? new EnderecoSolicitacaoResponse(
                        solicitacao.getCep(),
                        solicitacao.getLogradouro(),
                        solicitacao.getNumero(),
                        solicitacao.getComplemento(),
                        solicitacao.getBairro(),
                        solicitacao.getCidade(),
                        solicitacao.getUf())
                : null;

        return new SolicitacaoResponse(
                solicitacao.getId(),
                solicitacao.getPaciente().getId(),
                solicitacao.getProfissionais().stream().map(ProfissionalSolicitadoResponse::de).toList(),
                solicitacao.getDescricao(),
                solicitacao.getModalidade(),
                endereco,
                solicitacao.getPrazoDesejado(),
                solicitacao.getStatus(),
                solicitacao.getCriadoEm(),
                solicitacao.getAtualizadoEm(),
                totalPropostas,
                ativos.stream().anyMatch(atendimento -> atendimento.getStatus() == StatusAtendimento.EM_ANDAMENTO),
                ativos.stream()
                        .filter(atendimento -> atendimento.getStatus() != StatusAtendimento.EM_ANDAMENTO)
                        .map(Atendimento::getInicio)
                        .min(Comparator.naturalOrder())
                        .orElse(null)
        );
    }

}
