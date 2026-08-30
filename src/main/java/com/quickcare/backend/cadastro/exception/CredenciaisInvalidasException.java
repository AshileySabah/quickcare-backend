package com.quickcare.backend.cadastro.exception;

public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Senha inválida para o usuário já cadastrado");
    }

}
