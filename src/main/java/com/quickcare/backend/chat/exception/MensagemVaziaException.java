package com.quickcare.backend.chat.exception;

public class MensagemVaziaException extends RuntimeException {

    public MensagemVaziaException() {
        super("Informe um texto ou um anexo para enviar a mensagem.");
    }

}
