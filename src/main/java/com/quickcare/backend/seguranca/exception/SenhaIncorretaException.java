package com.quickcare.backend.seguranca.exception;

public class SenhaIncorretaException extends RuntimeException {

    public SenhaIncorretaException() {
        super("A senha atual está incorreta.");
    }

}
