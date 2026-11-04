package com.quickcare.backend.notificacao;

import com.quickcare.backend.notificacao.dto.NotificacaoResponse;

record NotificacaoCriada(Long usuarioId, NotificacaoResponse notificacao, Email email) {

    record Email(String destinatario, String nome) {
    }

}
