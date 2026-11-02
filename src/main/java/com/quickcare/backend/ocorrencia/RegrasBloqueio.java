package com.quickcare.backend.ocorrencia;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Quando as ocorrências bloqueiam o perfil: 3 válidas em 90 dias. As que
 * causaram um bloqueio continuam contando enquanto ele dura e deixam de contar
 * quando ele termina (o contador zera no fim, não no começo); as que surgem
 * durante o bloqueio contam para o próximo. O bloqueio dura 7 dias por nível;
 * o nível sobe a cada bloqueio e volta a 1 se o perfil passou 60 dias seguidos
 * sem ocorrência desde o anterior. Bloqueio desfeito não conta para nada.
 */
public final class RegrasBloqueio {

    public static final int LIMITE = 3;
    public static final Duration JANELA_CONTAGEM = Duration.ofDays(90);
    public static final Duration DURACAO_POR_NIVEL = Duration.ofDays(7);
    public static final Duration SEM_OCORRENCIA_PARA_ZERAR = Duration.ofDays(60);

    private RegrasBloqueio() {
    }

    /**
     * As que contam agora: dos últimos 90 dias, menos as que entraram no
     * bloqueio de {@code corte} (o último que já terminou, ver {@link #corte}).
     * Não corta as "depois de agora": o banco arredonda o horário
     * (microssegundos) e a recém-criada pode voltar um instante à frente.
     */
    public static List<Instant> contando(List<Instant> validas, Bloqueio corte, Instant agora) {
        Instant desde = agora.minus(JANELA_CONTAGEM);
        return validas.stream()
                .filter(momento -> momento.isAfter(desde))
                .filter(momento -> !jaGerouBloqueio(momento, corte))
                .sorted()
                .toList();
    }

    /**
     * De onde a contagem parte: o último bloqueio que já terminou. Se o último
     * ainda está valendo, as que o causaram seguem contando, então o corte é o
     * anterior a ele.
     */
    public static Bloqueio corte(Bloqueio ultimo, Bloqueio anteriorAoUltimo, Instant agora) {
        return ultimo != null && ultimo.ativoEm(agora) ? anteriorAoUltimo : ultimo;
    }

    /** O nível do bloqueio que deve começar agora, se as ocorrências chegaram ao limite. */
    public static Optional<Integer> nivelDoNovoBloqueio(List<Instant> validas, Bloqueio ultimo, Instant agora) {
        if (ultimo != null && ultimo.ativoEm(agora)) {
            return Optional.empty();
        }
        if (contando(validas, ultimo, agora).size() < LIMITE) {
            return Optional.empty();
        }
        if (ultimo == null) {
            return Optional.of(1);
        }

        List<Instant> marcos = new ArrayList<>(validas.stream()
                .filter(momento -> momento.isAfter(ultimo.getInicio()))
                .toList());
        marcos.add(ultimo.terminoEfetivo());
        marcos.sort(Comparator.naturalOrder());
        for (int i = 1; i < marcos.size(); i++) {
            if (Duration.between(marcos.get(i - 1), marcos.get(i)).compareTo(SEM_OCORRENCIA_PARA_ZERAR) >= 0) {
                return Optional.of(1);
            }
        }
        return Optional.of(ultimo.getNivel() + 1);
    }

    /**
     * Se as ocorrências válidas hoje ainda bastariam para o bloqueio no momento
     * em que ele começou (contando a partir do {@code anterior}). Não bastam
     * quando uma das que o causaram foi anulada: aí ele é desfeito.
     */
    public static boolean aindaJustificado(List<Instant> validas, Bloqueio anterior, Bloqueio bloqueio) {
        List<Instant> ateOInicio = validas.stream().filter(momento -> jaGerouBloqueio(momento, bloqueio)).toList();
        return contando(ateOInicio, anterior, bloqueio.getInicio()).size() >= LIMITE;
    }

    /**
     * A ocorrência é de até o início do bloqueio (entrou nele). A que disparou
     * tem o mesmo horário do início, daí a tolerância de 1 ms.
     */
    public static boolean jaGerouBloqueio(Instant momento, Bloqueio bloqueio) {
        return bloqueio != null && momento.isBefore(bloqueio.getInicio().plusMillis(1));
    }

    public static Duration duracao(int nivel) {
        return DURACAO_POR_NIVEL.multipliedBy(nivel);
    }

}
