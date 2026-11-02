package com.quickcare.backend.notificacao.dto;

import com.quickcare.backend.notificacao.Notificacao;
import com.quickcare.backend.notificacao.TipoNotificacao;
import com.quickcare.backend.usuario.TipoPerfil;

import java.time.Instant;

public record NotificacaoResponse(
        Long id,
        TipoPerfil perfil,
        TipoNotificacao tipo,
        String titulo,
        String mensagem,
        String link,
        boolean lida,
        Instant criadoEm
) {

    public static NotificacaoResponse de(Notificacao notificacao) {
        return new NotificacaoResponse(
                notificacao.getId(),
                notificacao.getPerfil(),
                notificacao.getTipo(),
                notificacao.getTitulo(),
                notificacao.getMensagem(),
                notificacao.getLink(),
                notificacao.lida(),
                notificacao.getCriadoEm());
    }

}
