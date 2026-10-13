package com.quickcare.backend.proposta.dto;

import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.solicitacao.dto.ProfissionalSolicitadoResponse;

import java.time.LocalDate;
import java.util.List;

/** O que o profissional vê da solicitação na lista das suas propostas. */
public record SolicitacaoResumoResponse(
        Long id,
        String pacienteNome,
        List<ProfissionalSolicitadoResponse> profissionais,
        ModalidadeSolicitacao modalidade,
        LocalDate prazoDesejado,
        StatusSolicitacao status
) {

    public static SolicitacaoResumoResponse de(Solicitacao solicitacao) {
        return new SolicitacaoResumoResponse(
                solicitacao.getId(),
                solicitacao.getPaciente().getUsuario().getNome(),
                solicitacao.getProfissionais().stream().map(ProfissionalSolicitadoResponse::de).toList(),
                solicitacao.getModalidade(),
                solicitacao.getPrazoDesejado(),
                solicitacao.getStatus()
        );
    }

}
