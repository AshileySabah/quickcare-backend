package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.atendimento.Atendimento;

import java.time.Duration;
import java.time.Instant;

public final class RegrasOcorrencia {

    public static final Duration JANELA = Duration.ofHours(48);

    private RegrasOcorrencia() {
    }

    public static boolean cancelamentoConta(Atendimento atendimento, Instant agora) {
        boolean dentroDaTolerancia = agora.isBefore(atendimento.getAceitoEm().plus(JANELA));
        boolean aceitoEmCimaDaHora = atendimento.getAceitoEm().isAfter(atendimento.getInicio().minus(JANELA));
        return !dentroDaTolerancia && !aceitoEmCimaDaHora;
    }

    public static boolean bloqueioTardio(Atendimento atendimento, Instant agora) {
        boolean faltaMenosDe48h = agora.isAfter(atendimento.getInicio().minus(JANELA));
        boolean aceitoEmCimaDaHora = atendimento.getAceitoEm().isAfter(atendimento.getInicio().minus(JANELA));
        return faltaMenosDe48h && !aceitoEmCimaDaHora;
    }

    public static boolean alteracaoTardia(Atendimento atendimento, Instant momentoDaAlteracao) {
        return momentoDaAlteracao.isAfter(atendimento.getInicio().minus(JANELA));
    }

}
