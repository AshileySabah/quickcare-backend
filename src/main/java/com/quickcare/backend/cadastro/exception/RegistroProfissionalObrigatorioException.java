package com.quickcare.backend.cadastro.exception;

public class RegistroProfissionalObrigatorioException extends RuntimeException {

    public RegistroProfissionalObrigatorioException() {
        super("Número de registro profissional é obrigatório para essa categoria");
    }

}
