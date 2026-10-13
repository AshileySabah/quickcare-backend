package com.quickcare.backend.solicitacao;

import com.quickcare.backend.profissional.ModalidadeAtendimento;

import java.util.EnumSet;
import java.util.Set;

public enum ModalidadeSolicitacao {
    PRESENCIAL,
    ONLINE;

    /** Modalidades de solicitação que um profissional com esta forma de atendimento pode atender. */
    public static Set<ModalidadeSolicitacao> atendidasPor(ModalidadeAtendimento modalidadeAtendimento) {
        return switch (modalidadeAtendimento) {
            case PRESENCIAL -> EnumSet.of(PRESENCIAL);
            case REMOTO -> EnumSet.of(ONLINE);
            case AMBOS -> EnumSet.allOf(ModalidadeSolicitacao.class);
        };
    }
}
