package com.quickcare.backend.cadastro.exception;

public class PerfilJaExistenteException extends RuntimeException {

    public PerfilJaExistenteException(String tipoPerfil) {
        super("Usuário já possui um perfil de " + tipoPerfil);
    }

}
