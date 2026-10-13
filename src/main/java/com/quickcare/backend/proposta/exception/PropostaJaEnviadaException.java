package com.quickcare.backend.proposta.exception;

public class PropostaJaEnviadaException extends RuntimeException {

    public PropostaJaEnviadaException() {
        super("Você já enviou uma proposta para esta solicitação. Edite ou cancele a existente.");
    }

}
