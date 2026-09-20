package com.quickcare.backend.cadastro.exception;

public class RaioAtendimentoObrigatorioException extends RuntimeException {

    public RaioAtendimentoObrigatorioException() {
        super("Raio de atendimento é obrigatório para profissionais com atendimento presencial");
    }

}
