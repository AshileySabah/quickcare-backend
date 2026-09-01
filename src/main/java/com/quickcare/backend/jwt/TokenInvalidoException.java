package com.quickcare.backend.jwt;

public class TokenInvalidoException extends RuntimeException {

    public TokenInvalidoException() {
        super("Token inválido ou expirado");
    }

}
