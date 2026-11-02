package com.quickcare.backend.ocorrencia;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasBloqueioTest {

    private static final Instant AGORA = Instant.parse("2026-11-02T12:00:00Z");

    @Test
    void terceiraOcorrenciaEm90DiasBloqueiaNoNivel1() {
        List<Instant> validas = List.of(diasAtras(80), diasAtras(10), AGORA);

        assertThat(RegrasBloqueio.nivelDoNovoBloqueio(validas, null, AGORA)).contains(1);
        assertThat(RegrasBloqueio.duracao(1)).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void ocorrenciasDeMaisDe90DiasNaoContam() {
        List<Instant> validas = List.of(diasAtras(95), diasAtras(10), AGORA);

        assertThat(RegrasBloqueio.contando(validas, null, AGORA)).hasSize(2);
        assertThat(RegrasBloqueio.nivelDoNovoBloqueio(validas, null, AGORA)).isEmpty();
    }

    @Test
    void oContadorZeraDepoisDoBloqueioEONivelSobe() {
        Bloqueio anterior = bloqueio(1, diasAtras(40));
        List<Instant> validas = List.of(diasAtras(45), diasAtras(42), diasAtras(40),
                diasAtras(20), diasAtras(5), AGORA);

        assertThat(RegrasBloqueio.contando(validas, anterior, AGORA)).hasSize(3);
        assertThat(RegrasBloqueio.nivelDoNovoBloqueio(validas, anterior, AGORA)).contains(2);
        assertThat(RegrasBloqueio.duracao(2)).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void sessentaDiasSemOcorrenciaVoltamONivelA1() {
        Bloqueio anterior = bloqueio(2, diasAtras(98));
        List<Instant> validas = List.of(diasAtras(20), diasAtras(10), AGORA);

        assertThat(RegrasBloqueio.nivelDoNovoBloqueio(validas, anterior, AGORA)).contains(1);
    }

    @Test
    void comBloqueioValendoNaoAbreOutro() {
        Bloqueio valendo = bloqueio(1, diasAtras(2));
        List<Instant> validas = List.of(diasAtras(1), diasAtras(1).plusSeconds(60), AGORA);

        assertThat(RegrasBloqueio.nivelDoNovoBloqueio(validas, valendo, AGORA)).isEmpty();
    }

    @Test
    void bloqueioRemovidoPeloAdminDeixaDeValer() {
        Bloqueio removido = bloqueio(1, diasAtras(2));
        removido.remover(null, "Cancelamento justificado por internação.", diasAtras(1));

        assertThat(removido.ativoEm(AGORA)).isFalse();
        assertThat(removido.terminoEfetivo()).isEqualTo(diasAtras(1));
    }

    @Test
    void asQueCausaramOBloqueioContamEnquantoEleDuraEZeramNoFim() {
        Bloqueio valendo = bloqueio(1, diasAtras(2));
        List<Instant> validas = List.of(diasAtras(30), diasAtras(10), diasAtras(2), diasAtras(1));

        Bloqueio corteDuranteOBloqueio = RegrasBloqueio.corte(valendo, null, AGORA);
        assertThat(corteDuranteOBloqueio).isNull();
        assertThat(RegrasBloqueio.contando(validas, corteDuranteOBloqueio, AGORA)).hasSize(4);

        Instant depoisDoFim = valendo.getFim().plusSeconds(60);
        Bloqueio corteDepois = RegrasBloqueio.corte(valendo, null, depoisDoFim);
        assertThat(corteDepois).isSameAs(valendo);
        assertThat(RegrasBloqueio.contando(validas, corteDepois, depoisDoFim)).containsExactly(diasAtras(1));
    }

    @Test
    void comDuasDepoisDeAnularUmaOBloqueioNaoSeSustenta() {
        Bloqueio valendo = bloqueio(1, diasAtras(1));

        assertThat(RegrasBloqueio.aindaJustificado(List.of(diasAtras(30), diasAtras(1)), null, valendo)).isFalse();
        assertThat(RegrasBloqueio.aindaJustificado(List.of(diasAtras(30), diasAtras(5), diasAtras(1)), null, valendo)).isTrue();
    }

    private static Bloqueio bloqueio(int nivel, Instant inicio) {
        return Bloqueio.builder().nivel(nivel).inicio(inicio).fim(inicio.plus(RegrasBloqueio.duracao(nivel))).build();
    }

    private static Instant diasAtras(int dias) {
        return AGORA.minus(Duration.ofDays(dias));
    }

}
