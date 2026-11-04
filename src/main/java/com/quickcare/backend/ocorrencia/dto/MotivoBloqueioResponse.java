package com.quickcare.backend.ocorrencia.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.ocorrencia.MotivoBloqueioTardio;
import com.quickcare.backend.usuario.TipoPerfil;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MotivoBloqueioResponse(
        Long id,
        MotivoBloqueioTardio.Status status,
        String texto,
        String bloqueado,
        Long solicitacaoId,
        String servico,
        Instant inicioDoAtendimento,
        Instant aceitoEm,
        Instant criadoEm,
        Instant decididoEm,
        String decididoPor,
        String justificativaDecisao
) {

    public static MotivoBloqueioResponse de(MotivoBloqueioTardio motivo) {
        Atendimento atendimento = motivo.getAtendimento();
        String bloqueado = motivo.getPerfil() == TipoPerfil.PACIENTE
                ? atendimento.getProposta().getProfissional().getUsuario().getNome()
                : atendimento.getSolicitacao().getPaciente().getUsuario().getNome();
        return new MotivoBloqueioResponse(motivo.getId(), motivo.getStatus(), motivo.getTexto(), bloqueado,
                atendimento.getSolicitacao().getId(), Notificador.servico(atendimento.getSolicitacao()),
                atendimento.getInicio(), atendimento.getAceitoEm(), motivo.getCriadoEm(), motivo.getDecididoEm(),
                motivo.getDecididoPor() == null ? null : motivo.getDecididoPor().getNome(),
                motivo.getJustificativaDecisao());
    }

}
