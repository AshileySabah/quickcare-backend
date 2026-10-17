package com.quickcare.backend.proposta.exception;

public class PropostaNaoEncontradaException extends RuntimeException {

    public PropostaNaoEncontradaException() {
        super("Proposta não encontrada.");
    }

}
