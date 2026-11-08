package com.quickcare.backend.chat.exception;

public class MensagemNaoEncontradaException extends RuntimeException {

    public MensagemNaoEncontradaException() {
        super("Mensagem não encontrada.");
    }

}
