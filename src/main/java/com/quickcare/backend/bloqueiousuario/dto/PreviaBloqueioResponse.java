package com.quickcare.backend.bloqueiousuario.dto;

public record PreviaBloqueioResponse(
        String nome,
        int propostasPendentes,
        int atendimentosVigentes,
        boolean contaOcorrencia
) {
}
