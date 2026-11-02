package com.quickcare.backend.proposta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.solicitacao.dto.ProfissionalSolicitadoResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * O que o profissional vê da solicitação na lista das suas propostas. Quem é o
 * paciente (id, nome e foto) só vem depois que a proposta é aceita; antes
 * disso esses campos nem aparecem no JSON.
 */
public record SolicitacaoResumoResponse(
        Long id,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long pacienteId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String pacienteNome,
        @JsonInclude(JsonInclude.Include.NON_NULL) String pacienteAvatarUrl,
        List<ProfissionalSolicitadoResponse> profissionais,
        ModalidadeSolicitacao modalidade,
        LocalDate prazoDesejado,
        StatusSolicitacao status
) {

    public static SolicitacaoResumoResponse de(Solicitacao solicitacao, boolean revelaPaciente, String pacienteAvatarUrl) {
        return new SolicitacaoResumoResponse(
                solicitacao.getId(),
                revelaPaciente ? solicitacao.getPaciente().getId() : null,
                revelaPaciente ? solicitacao.getPaciente().getUsuario().getNome() : null,
                revelaPaciente ? pacienteAvatarUrl : null,
                solicitacao.getProfissionais().stream().map(ProfissionalSolicitadoResponse::de).toList(),
                solicitacao.getModalidade(),
                solicitacao.getPrazoDesejado(),
                solicitacao.getStatus()
        );
    }

}
