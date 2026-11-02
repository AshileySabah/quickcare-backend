package com.quickcare.backend.atendimento.exception;

public class AtendimentoNaoEncontradoException extends RuntimeException {

    public AtendimentoNaoEncontradoException() {
        super("Atendimento não encontrado.");
    }

}
