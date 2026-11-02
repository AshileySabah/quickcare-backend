package com.quickcare.backend.problema.dto;

import com.quickcare.backend.administrador.dto.ParteResponse;
import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.problema.MotivoProblema;
import com.quickcare.backend.problema.ProblemaAtendimento;
import com.quickcare.backend.problema.StatusProblema;

import java.time.Instant;

/** Uma linha da fila de disputas do admin. */
public record DisputaResumoResponse(
        Long id,
        StatusProblema status,
        Long atendimentoId,
        Long solicitacaoId,
        String servico,
        Instant inicio,
        Instant termino,
        ParteResponse paciente,
        ParteResponse profissional,
        LadoAtendimento relatadoPor,
        MotivoProblema motivo,
        Instant criadoEm,
        Instant prazoResposta,
        Instant contestadoEm
) {

    public static DisputaResumoResponse de(ProblemaAtendimento problema) {
        Atendimento atendimento = problema.getAtendimento();
        return new DisputaResumoResponse(
                problema.getId(),
                problema.getStatus(),
                atendimento.getId(),
                atendimento.getSolicitacao().getId(),
                Notificador.servico(atendimento.getProposta()),
                atendimento.getInicio(),
                atendimento.getTermino(),
                ParteResponse.de(atendimento.getSolicitacao().getPaciente().getUsuario()),
                ParteResponse.de(atendimento.getProposta().getProfissional().getUsuario()),
                problema.getRelatadoPor(),
                problema.getMotivo(),
                problema.getCriadoEm(),
                problema.getPrazoResposta(),
                problema.getContestadoEm());
    }

}
