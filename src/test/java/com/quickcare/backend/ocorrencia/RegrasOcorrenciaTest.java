package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.atendimento.Atendimento;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasOcorrenciaTest {

    private static final Instant INICIO = Instant.parse("2026-11-20T11:00:00Z");

    private static Atendimento aceitoAntesDoInicio(Duration antecedencia) {
        return Atendimento.builder().inicio(INICIO).termino(INICIO.plus(Duration.ofHours(8)))
                .aceitoEm(INICIO.minus(antecedencia)).build();
    }

    @Test
    void cancelarNas48hDepoisDoAceiteNaoConta() {
        Atendimento atendimento = aceitoAntesDoInicio(Duration.ofDays(10));

        assertThat(RegrasOcorrencia.cancelamentoConta(atendimento, atendimento.getAceitoEm().plus(Duration.ofHours(47))))
                .isFalse();
        assertThat(RegrasOcorrencia.cancelamentoConta(atendimento, atendimento.getAceitoEm().plus(Duration.ofHours(49))))
                .isTrue();
    }

    @Test
    void aceiteFeitoComMenosDe48hParaOInicioNaoConta() {
        Atendimento emCimaDaHora = aceitoAntesDoInicio(Duration.ofHours(30));

        assertThat(RegrasOcorrencia.cancelamentoConta(emCimaDaHora, INICIO.plus(Duration.ofHours(30)))).isFalse();
    }

    @Test
    void bloqueioTardioEhAMenosDe48hDoInicioDeUmAceiteFeitoAntes() {
        Atendimento atendimento = aceitoAntesDoInicio(Duration.ofDays(10));
        Atendimento aceitoEmCimaDaHora = aceitoAntesDoInicio(Duration.ofHours(30));

        assertThat(RegrasOcorrencia.bloqueioTardio(atendimento, INICIO.minus(Duration.ofHours(49)))).isFalse();
        assertThat(RegrasOcorrencia.bloqueioTardio(atendimento, INICIO.minus(Duration.ofHours(47)))).isTrue();
        assertThat(RegrasOcorrencia.bloqueioTardio(atendimento, INICIO.plus(Duration.ofHours(2)))).isTrue();
        assertThat(RegrasOcorrencia.bloqueioTardio(aceitoEmCimaDaHora, INICIO.minus(Duration.ofHours(10)))).isFalse();
    }

    @Test
    void alteracaoTardiaEhAMenosDe48hDoInicio() {
        Atendimento atendimento = aceitoAntesDoInicio(Duration.ofDays(10));

        assertThat(RegrasOcorrencia.alteracaoTardia(atendimento, INICIO.minus(Duration.ofHours(47)))).isTrue();
        assertThat(RegrasOcorrencia.alteracaoTardia(atendimento, INICIO.minus(Duration.ofHours(49)))).isFalse();
    }

}
