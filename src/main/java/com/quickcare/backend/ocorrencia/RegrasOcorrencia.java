package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.atendimento.Atendimento;

import java.time.Duration;
import java.time.Instant;

/**
 * Quando uma desistência conta como ocorrência. Ficam aqui, num lugar só,
 * para o back aplicar e o front avisar antes ("isto conta como ocorrência").
 */
public final class RegrasOcorrencia {

    /** Tolerância depois de cada aceitação, e a antecedência mínima que protege o outro lado. */
    public static final Duration JANELA = Duration.ofHours(48);

    private RegrasOcorrencia() {
    }

    /**
     * Cancelar conta, a não ser que: ainda esteja nas 48h depois da última
     * aceitação, ou a própria aceitação tenha sido feita com menos de 48h para
     * o início (quem aceitou em cima da hora não é punido por desistir).
     */
    public static boolean cancelamentoConta(Atendimento atendimento, Instant agora) {
        boolean dentroDaTolerancia = agora.isBefore(atendimento.getAceitoEm().plus(JANELA));
        boolean aceitoEmCimaDaHora = atendimento.getAceitoEm().isAfter(atendimento.getInicio().minus(JANELA));
        return !dentroDaTolerancia && !aceitoEmCimaDaHora;
    }

    /** Alterar a solicitação (ou trocar o aceito) a menos de 48h do início do atendimento. */
    public static boolean alteracaoTardia(Atendimento atendimento, Instant momentoDaAlteracao) {
        return momentoDaAlteracao.isAfter(atendimento.getInicio().minus(JANELA));
    }

}
