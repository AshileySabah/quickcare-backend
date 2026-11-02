package com.quickcare.backend.bloqueiousuario.dto;

/**
 * O que acontece se bloquear agora, para o modal avisar antes: quantas
 * propostas pendentes e atendimentos vigentes entre os dois acabam, e se isso
 * conta ocorrência para quem bloqueia (regra das 48h).
 */
public record PreviaBloqueioResponse(
        String nome,
        int propostasPendentes,
        int atendimentosVigentes,
        boolean contaOcorrencia
) {
}
