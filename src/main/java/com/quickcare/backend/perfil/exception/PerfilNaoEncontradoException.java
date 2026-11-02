package com.quickcare.backend.perfil.exception;

/** Perfil inexistente ou que quem pediu não pode ver — a resposta é a mesma, para não revelar que ele existe. */
public class PerfilNaoEncontradoException extends RuntimeException {

    public PerfilNaoEncontradoException() {
        super("Perfil não encontrado.");
    }

}
