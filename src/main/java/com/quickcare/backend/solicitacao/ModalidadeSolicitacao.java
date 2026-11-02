package com.quickcare.backend.solicitacao;

import com.quickcare.backend.profissional.ModalidadeAtendimento;

import java.util.EnumSet;
import java.util.Set;

public enum ModalidadeSolicitacao {
    PRESENCIAL,
    ONLINE;

    public static Set<ModalidadeSolicitacao> atendidasPor(ModalidadeAtendimento modalidadeAtendimento) {
        return switch (modalidadeAtendimento) {
            case PRESENCIAL -> EnumSet.of(PRESENCIAL);
            case REMOTO -> EnumSet.of(ONLINE);
            case AMBOS -> EnumSet.allOf(ModalidadeSolicitacao.class);
        };
    }
}
