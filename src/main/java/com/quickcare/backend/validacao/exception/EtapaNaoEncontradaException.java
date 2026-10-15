package com.quickcare.backend.validacao.exception;

public class EtapaNaoEncontradaException extends RuntimeException {

    public EtapaNaoEncontradaException() {
        super("Esta etapa de validação não se aplica a este cadastro.");
    }

}
