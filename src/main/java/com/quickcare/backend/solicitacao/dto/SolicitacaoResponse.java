package com.quickcare.backend.solicitacao.dto;

import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.StatusSolicitacao;

import java.time.Instant;
import java.time.LocalDate;
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
        Instant atualizadoEm
) {

    public static SolicitacaoResponse de(Solicitacao solicitacao) {
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
                solicitacao.getAtualizadoEm()
        );
    }

}
