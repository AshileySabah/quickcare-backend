package com.quickcare.backend.cadastro.exception;

public class CpfJaCadastradoException extends RuntimeException {

    public CpfJaCadastradoException() {
        super("CPF já cadastrado para um e-mail diferente");
    }

}
