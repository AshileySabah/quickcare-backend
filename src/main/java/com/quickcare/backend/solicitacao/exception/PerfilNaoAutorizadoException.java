package com.quickcare.backend.solicitacao.exception;

public class PerfilNaoAutorizadoException extends RuntimeException {

    public PerfilNaoAutorizadoException(String mensagem) {
        super(mensagem);
    }

}
