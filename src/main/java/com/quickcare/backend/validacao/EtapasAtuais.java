package com.quickcare.backend.validacao;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Regras sobre o conjunto de etapas de um cadastro. */
final class EtapasAtuais {

    private EtapasAtuais() {
    }

    /** A avaliação mais recente de cada tipo (a lista vem em ordem de criação). */
    static Map<TipoEtapaValidacao, EtapaValidacao> de(List<EtapaValidacao> todasEtapas) {
        Map<TipoEtapaValidacao, EtapaValidacao> atuais = new EnumMap<>(TipoEtapaValidacao.class);
        todasEtapas.forEach(etapa -> atuais.put(etapa.getTipo(), etapa));
        return atuais;
    }

    /**
     * Alguma reprovada → REPROVADO; todas aprovadas → APROVADO; senão PENDENTE.
     * Cadastros anteriores à validação não têm etapas e mantêm o status atual.
     */
    static StatusValidacao statusDo(Collection<EtapaValidacao> atuais, StatusValidacao statusAtual) {
        if (atuais.isEmpty()) {
            return statusAtual;
        }
        if (atuais.stream().anyMatch(etapa -> etapa.getStatus() == StatusEtapa.REPROVADA)) {
            return StatusValidacao.REPROVADO;
        }
        if (atuais.stream().allMatch(etapa -> etapa.getStatus() == StatusEtapa.APROVADA)) {
            return StatusValidacao.APROVADO;
        }
        return StatusValidacao.PENDENTE;
    }

}
