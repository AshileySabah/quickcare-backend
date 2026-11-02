package com.quickcare.backend.notificacao.exception;

public class NotificacaoNaoEncontradaException extends RuntimeException {

    public NotificacaoNaoEncontradaException() {
        super("Notificação não encontrada.");
    }

}
