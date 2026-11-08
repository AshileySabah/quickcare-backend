package com.quickcare.backend.atuacao;

public class AtuacaoNaoEncontradaException extends RuntimeException {

    public AtuacaoNaoEncontradaException() {
        super("Atuação não encontrada no seu perfil.");
    }

}
