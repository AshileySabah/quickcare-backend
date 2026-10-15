package com.quickcare.backend.validacao.dto;

import com.quickcare.backend.validacao.TipoEtapaValidacao;
import com.quickcare.backend.validacao.externa.Parecer;
import com.quickcare.backend.validacao.externa.VerificacaoExterna;

import java.time.Instant;
import java.util.List;

/**
 * Resultado das consultas automáticas de uma etapa. Não é salvo: serve para o
 * admin decidir e já vem com um texto sugerido para a justificativa.
 */
public record PreValidacaoResponse(
        TipoEtapaValidacao tipo,
        Parecer parecer,
        List<VerificacaoExterna> verificacoes,
        String sugestaoJustificativa,
        Instant consultadoEm
) {
}
