package com.quickcare.backend.chat.exception;

/**
 * O usuário não participa da conversa (ou ela não existe, ou a proposta não está aceita).
 * Mapeada para 403.
 */
public class ConversaAcessoNegadoException extends RuntimeException {

    public ConversaAcessoNegadoException() {
        super("Você não participa desta conversa.");
    }

}
