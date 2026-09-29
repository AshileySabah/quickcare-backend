package com.quickcare.backend.auth.exception;

public class TokenRecuperacaoInvalidoException extends RuntimeException {
    public TokenRecuperacaoInvalidoException() {
        super("Token inválido ou expirado");
    }
}
