package com.quickcare.backend.cadastro.exception;

public class EspecialidadeInvalidaException extends RuntimeException {

    public EspecialidadeInvalidaException() {
        super("Uma ou mais especialidades informadas não existem");
    }

    public EspecialidadeInvalidaException(String mensagem) {
        super(mensagem);
    }

}
