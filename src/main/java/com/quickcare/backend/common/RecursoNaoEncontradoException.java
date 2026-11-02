package com.quickcare.backend.common;

/** O item pedido (ocorrência, bloqueio, disputa, anexo...) não existe ou não é visível para quem pediu. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

}
