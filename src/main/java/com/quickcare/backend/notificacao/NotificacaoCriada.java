package com.quickcare.backend.notificacao;

import com.quickcare.backend.notificacao.dto.NotificacaoResponse;

/**
 * Publicado ao gravar uma notificação; o envio em tempo real (e o e-mail,
 * quando houver) só acontece depois do commit, para não avisar de algo que
 * acabou desfeito.
 */
record NotificacaoCriada(Long usuarioId, NotificacaoResponse notificacao, Email email) {

    /** Só banimento, validação e bloqueio também vão por e-mail. */
    record Email(String destinatario, String nome) {
    }

}
