package com.quickcare.backend.validacao;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class EtapasAtuais {

    private EtapasAtuais() {
    }

    static Map<TipoEtapaValidacao, EtapaValidacao> de(List<EtapaValidacao> todasEtapas) {
        Map<TipoEtapaValidacao, EtapaValidacao> atuais = new EnumMap<>(TipoEtapaValidacao.class);
        todasEtapas.forEach(etapa -> atuais.put(etapa.getTipo(), etapa));
        return atuais;
    }

    static boolean todasAprovadas(Collection<EtapaValidacao> atuais) {
        return atuais.stream().allMatch(etapa -> etapa.getStatus() == StatusEtapa.APROVADA);
    }

    static StatusValidacao statusDo(Collection<EtapaValidacao> atuais, StatusValidacao statusAtual) {
        if (atuais.isEmpty() || statusAtual == StatusValidacao.APROVADO) {
            return statusAtual;
        }
        if (atuais.stream().anyMatch(etapa -> etapa.getStatus() == StatusEtapa.REPROVADA)) {
            return StatusValidacao.REPROVADO;
        }
        if (todasAprovadas(atuais)) {
            return StatusValidacao.APROVADO;
        }
        return StatusValidacao.PENDENTE;
    }

}
