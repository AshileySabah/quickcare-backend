package com.quickcare.backend.chat;

import java.time.Instant;

/**
 * O que um usuário pode fazer em um canal. {@code lerDesde} e {@code lerAte} limitam o histórico
 * visível (nulos = sem limite).
 */
public record AcessoAoCanal(boolean podeEscrever, Instant lerDesde, Instant lerAte) {

    public boolean enxerga(Instant instante) {
        return (lerDesde == null || !instante.isBefore(lerDesde))
                && (lerAte == null || !instante.isAfter(lerAte));
    }

}
