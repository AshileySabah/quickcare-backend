package com.quickcare.backend.common.validation;

import java.time.LocalDate;

public final class Maioridade {

    public static final int IDADE_MINIMA = 18;

    private Maioridade() {
    }

    public static boolean atingida(LocalDate dataNascimento, LocalDate hoje) {
        return !dataNascimento.plusYears(IDADE_MINIMA).isAfter(hoje);
    }
}
