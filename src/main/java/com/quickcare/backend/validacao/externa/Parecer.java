package com.quickcare.backend.validacao.externa;

/** Opinião de uma consulta automática. Nunca decide a etapa — só orienta o admin. */
public enum Parecer {
    FAVORAVEL,
    DESFAVORAVEL,
    /** A API falhou (fora do ar, timeout, limite de requisições): validar manualmente. */
    INDISPONIVEL,
    /** Não existe API pública para este dado: validar manualmente. */
    SEM_API;

    /** Desfavorável pesa mais que indisponível, que pesa mais que favorável. */
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
