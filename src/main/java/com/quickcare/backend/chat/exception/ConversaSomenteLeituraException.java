package com.quickcare.backend.chat.exception;

/**
 * O usuário saiu da conversa (ou ela não tem mais ninguém para responder): ele ainda vê o
 * histórico, mas não escreve. Mapeada para 403.
 */
public class ConversaSomenteLeituraException extends RuntimeException {

    public ConversaSomenteLeituraException() {
        super("Esta conversa está encerrada para você. Você só pode ver o histórico.");
    }

}
