package com.quickcare.backend.validacao;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class EtapasAtuais {

    private EtapasAtuais() {
    }

    static Map<ChaveEtapa, EtapaValidacao> de(List<EtapaValidacao> todasEtapas) {
        Map<ChaveEtapa, EtapaValidacao> atuais = new LinkedHashMap<>();
        todasEtapas.stream()
                .filter(EtapaValidacao::vigente)
                .forEach(etapa -> atuais.put(etapa.chave(), etapa));
        return atuais;
    }

    static boolean todasAprovadas(Collection<EtapaValidacao> atuais) {
        return atuais.stream().allMatch(etapa -> etapa.getStatus() == StatusEtapa.APROVADA);
    }

    static boolean daConta(EtapaValidacao etapa) {
        return etapa.getAtuacao() == null;
    }

    static boolean contaAprovada(Collection<EtapaValidacao> atuais) {
        return todasAprovadas(atuais.stream().filter(EtapasAtuais::daConta).toList());
    }

    static StatusValidacao statusDo(Collection<EtapaValidacao> atuais, StatusValidacao statusAtual,
                                    boolean exigeAtuacao, boolean temAtuacaoLiberadaSemEtapa) {
        if (atuais.isEmpty() || statusAtual == StatusValidacao.APROVADO) {
            return statusAtual;
        }

        List<EtapaValidacao> daConta = atuais.stream().filter(EtapasAtuais::daConta).toList();
        List<EtapaValidacao> registros = atuais.stream().filter(etapa -> !daConta(etapa)).toList();

        if (daConta.stream().anyMatch(etapa -> etapa.getStatus() == StatusEtapa.REPROVADA)) {
            return StatusValidacao.REPROVADO;
        }

        boolean algumaAtuacaoLiberada = temAtuacaoLiberadaSemEtapa
                || registros.stream().anyMatch(etapa -> etapa.getStatus() == StatusEtapa.APROVADA);
        boolean registrosTodosReprovados = !registros.isEmpty()
                && registros.stream().allMatch(etapa -> etapa.getStatus() == StatusEtapa.REPROVADA);

        if (exigeAtuacao && !algumaAtuacaoLiberada && registrosTodosReprovados) {
            return StatusValidacao.REPROVADO;
        }
        if (todasAprovadas(daConta) && (!exigeAtuacao || algumaAtuacaoLiberada)) {
            return StatusValidacao.APROVADO;
        }
        return StatusValidacao.PENDENTE;
    }

}
