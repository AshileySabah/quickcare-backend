package com.quickcare.backend.cadastro.exception;

public class EmailJaCadastradoException extends RuntimeException {

    public EmailJaCadastradoException() {
        super("E-mail já cadastrado para um CPF diferente");
    }

}
