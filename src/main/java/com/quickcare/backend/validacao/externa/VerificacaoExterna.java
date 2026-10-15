package com.quickcare.backend.validacao.externa;

import java.util.List;

/**
 * Resultado da conferência de um dado (CPF, CNPJ, RG...). `motivos` explica o
 * parecer; `observacoes` traz informações auxiliares que não pesam nele.
 */
public record VerificacaoExterna(
        String campo,
        String provedor,
        Parecer parecer,
        List<String> motivos,
        List<String> observacoes
) {

    public static VerificacaoExterna semApi(String campo, String motivo, List<String> observacoes) {
        return new VerificacaoExterna(campo, null, Parecer.SEM_API, List.of(motivo), observacoes);
    }

    public static VerificacaoExterna indisponivel(String campo, String provedor, String motivo) {
        return new VerificacaoExterna(campo, provedor, Parecer.INDISPONIVEL,
                List.of(motivo), List.of("Tente de novo em instantes ou valide manualmente."));
    }

}
