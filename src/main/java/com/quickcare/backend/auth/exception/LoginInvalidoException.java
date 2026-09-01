package com.quickcare.backend.auth.exception;

public class LoginInvalidoException extends RuntimeException {

    public LoginInvalidoException() {
        super("E-mail ou senha inválidos");
    }

}
