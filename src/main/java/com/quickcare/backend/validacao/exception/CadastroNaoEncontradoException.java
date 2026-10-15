package com.quickcare.backend.validacao.exception;

public class CadastroNaoEncontradoException extends RuntimeException {

    public CadastroNaoEncontradoException() {
        super("Cadastro não encontrado.");
    }

}
