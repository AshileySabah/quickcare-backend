package com.quickcare.backend.paciente.exception;

public class PacientePerfilNaoEncontradoException extends RuntimeException {
    public PacientePerfilNaoEncontradoException() {
        super("Perfil de paciente não encontrado para este usuário");
    }
}