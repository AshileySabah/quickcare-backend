package com.quickcare.backend.solicitacao.exception;

public class SolicitacaoNaoEncontradaException extends RuntimeException {

    public SolicitacaoNaoEncontradaException() {
        super("Solicitação não encontrada.");
    }

}
