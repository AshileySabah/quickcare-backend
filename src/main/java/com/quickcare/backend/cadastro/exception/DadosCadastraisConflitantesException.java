package com.quickcare.backend.cadastro.exception;

public class DadosCadastraisConflitantesException extends RuntimeException {

    public DadosCadastraisConflitantesException() {
        super("O e-mail e o CPF informados pertencem a cadastros diferentes");
    }

}
