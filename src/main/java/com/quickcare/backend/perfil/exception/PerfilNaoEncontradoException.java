package com.quickcare.backend.perfil.exception;

public class PerfilNaoEncontradoException extends RuntimeException {

    public PerfilNaoEncontradoException() {
        super("Perfil não encontrado.");
    }

}
