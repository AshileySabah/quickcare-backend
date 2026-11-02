package com.quickcare.backend.validacao.externa;

public enum Parecer {
    FAVORAVEL,
    DESFAVORAVEL,
    INDISPONIVEL,
    SEM_API;

    public static Parecer geral(Iterable<Parecer> pareceres) {
        boolean algumIndisponivel = false;
        boolean algumFavoravel = false;
        for (Parecer parecer : pareceres) {
            switch (parecer) {
                case DESFAVORAVEL -> {
                    return DESFAVORAVEL;
                }
                case INDISPONIVEL -> algumIndisponivel = true;
                case FAVORAVEL -> algumFavoravel = true;
                case SEM_API -> {
                }
            }
        }
        if (algumIndisponivel) {
            return INDISPONIVEL;
        }
        return algumFavoravel ? FAVORAVEL : SEM_API;
    }
}
