package com.quickcare.backend.chat.exception;

public class ExclusaoMensagemNaoAutorizadaException extends RuntimeException {

    public ExclusaoMensagemNaoAutorizadaException() {
        super("Apenas o remetente pode excluir a mensagem.");
    }

}
