package com.quickcare.backend.ocorrencia;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class RegrasBloqueio {

    public static final int LIMITE = 3;
    public static final Duration JANELA_CONTAGEM = Duration.ofDays(90);
    public static final Duration DURACAO_POR_NIVEL = Duration.ofDays(7);
    public static final Duration SEM_OCORRENCIA_PARA_ZERAR = Duration.ofDays(60);

    private RegrasBloqueio() {
    }

    public static List<Instant> contando(List<Instant> validas, Bloqueio corte, Instant agora) {
        Instant desde = agora.minus(JANELA_CONTAGEM);
        return validas.stream()
                .filter(momento -> momento.isAfter(desde))
                .filter(momento -> !jaGerouBloqueio(momento, corte))
                .sorted()
                .toList();
    }

    public static Bloqueio corte(Bloqueio ultimo, Bloqueio anteriorAoUltimo, Instant agora) {
        return ultimo != null && ultimo.ativoEm(agora) ? anteriorAoUltimo : ultimo;
    }

    public static Optional<Integer> nivelDoNovoBloqueio(List<Instant> validas, Bloqueio ultimo, Instant agora) {
        if (ultimo != null && ultimo.ativoEm(agora)) {
            return Optional.empty();
        }
        if (contando(validas, ultimo, agora).size() < LIMITE) {
            return Optional.empty();
        }
        return Optional.of(proximoNivel(validas, ultimo));
    }

    public static int proximoNivel(List<Instant> validas, Bloqueio ultimo) {
        if (ultimo == null) {
            return 1;
        }

        List<Instant> marcos = new ArrayList<>(validas.stream()
                .filter(momento -> momento.isAfter(ultimo.getInicio()))
                .toList());
        marcos.add(ultimo.terminoEfetivo());
        marcos.sort(Comparator.naturalOrder());
        for (int i = 1; i < marcos.size(); i++) {
            if (Duration.between(marcos.get(i - 1), marcos.get(i)).compareTo(SEM_OCORRENCIA_PARA_ZERAR) >= 0) {
                return 1;
            }
        }
        return ultimo.getNivel() + 1;
    }

    public static boolean aindaJustificado(List<Instant> validas, Bloqueio anterior, Bloqueio bloqueio) {
        List<Instant> ateOInicio = validas.stream().filter(momento -> jaGerouBloqueio(momento, bloqueio)).toList();
        return contando(ateOInicio, anterior, bloqueio.getInicio()).size() >= LIMITE;
    }

    public static boolean jaGerouBloqueio(Instant momento, Bloqueio bloqueio) {
        return bloqueio != null && momento.isBefore(bloqueio.getInicio().plusMillis(1));
    }

    public static Duration duracao(int nivel) {
        return DURACAO_POR_NIVEL.multipliedBy(nivel);
    }

}
