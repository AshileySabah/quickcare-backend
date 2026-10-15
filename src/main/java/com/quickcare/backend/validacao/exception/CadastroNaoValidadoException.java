package com.quickcare.backend.validacao.exception;

public class CadastroNaoValidadoException extends RuntimeException {

    public CadastroNaoValidadoException(String mensagem) {
        super(mensagem);
    }

}
