package com.quickcare.backend.avaliacao;

import com.quickcare.backend.atendimento.Atendimento;

import java.time.Duration;
import java.time.Instant;

public final class RegrasAvaliacao {

    public static final Duration JANELA = Duration.ofDays(30);
    public static final Duration PRIMEIRO_LEMBRETE = Duration.ofDays(3);
    public static final Duration SEGUNDO_LEMBRETE = Duration.ofDays(7);
    public static final int DESTAQUES_NO_PERFIL = 3;

    private RegrasAvaliacao() {
    }

    public static Instant prazo(Atendimento atendimento) {
        return atendimento.getTermino().plus(JANELA);
    }

}
