package com.quickcare.backend.avaliacao;

import com.quickcare.backend.atendimento.LadoAtendimento;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

public enum DestaqueAvaliacao {
    PONTUALIDADE("Pontualidade", true, Alvo.AMBOS),
    EDUCACAO("Educação", true, Alvo.AMBOS),
    BOA_COMUNICACAO("Boa comunicação", true, Alvo.AMBOS),
    ATENCAO("Atenção", true, Alvo.PROFISSIONAL),
    CUIDADO("Cuidado", true, Alvo.PROFISSIONAL),
    ORGANIZACAO("Organização", true, Alvo.PROFISSIONAL),
    COLABORACAO("Colaboração", true, Alvo.PACIENTE),
    INFORMACOES_CLARAS("Informações claras", true, Alvo.PACIENTE),
    ATRASO("Atraso", false, Alvo.AMBOS),
    COMUNICACAO_DIFICIL("Comunicação difícil", false, Alvo.AMBOS),
    POUCA_ATENCAO("Pouca atenção", false, Alvo.PROFISSIONAL),
    DESORGANIZACAO("Desorganização", false, Alvo.PROFISSIONAL),
    POUCA_COLABORACAO("Pouca colaboração", false, Alvo.PACIENTE),
    INFORMACOES_INCOMPLETAS("Informações incompletas", false, Alvo.PACIENTE);

    private enum Alvo {
        PACIENTE,
        PROFISSIONAL,
        AMBOS
    }

    @Getter
    private final String rotulo;
    @Getter
    private final boolean positivo;
    private final Alvo alvo;

    DestaqueAvaliacao(String rotulo, boolean positivo, Alvo alvo) {
        this.rotulo = rotulo;
        this.positivo = positivo;
        this.alvo = alvo;
    }

    public boolean serveParaAvaliar(LadoAtendimento avaliado) {
        return alvo == Alvo.AMBOS
                || (alvo == Alvo.PACIENTE && avaliado == LadoAtendimento.PACIENTE)
                || (alvo == Alvo.PROFISSIONAL && avaliado == LadoAtendimento.PROFISSIONAL);
    }

    public static List<DestaqueAvaliacao> paraAvaliar(LadoAtendimento avaliado) {
        return Arrays.stream(values()).filter(destaque -> destaque.serveParaAvaliar(avaliado)).toList();
    }
}
