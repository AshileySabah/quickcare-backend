package com.quickcare.backend.auth.exception;

public class TokenRedefinicaoInvalidoException extends RuntimeException {

    public TokenRedefinicaoInvalidoException() {
        super("Link de redefinição inválido ou expirado. Solicite um novo.");
    }

}
