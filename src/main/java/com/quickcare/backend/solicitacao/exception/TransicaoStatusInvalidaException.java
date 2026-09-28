package com.quickcare.backend.solicitacao.exception;

/**
 * Operação não permitida no status atual da solicitação (ex.: editar uma
 * solicitação já em andamento).
 */
public class TransicaoStatusInvalidaException extends RuntimeException {

    public TransicaoStatusInvalidaException(String mensagem) {
        super(mensagem);
    }

}
