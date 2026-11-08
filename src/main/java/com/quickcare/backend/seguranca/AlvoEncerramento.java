package com.quickcare.backend.seguranca;

import com.quickcare.backend.usuario.TipoPerfil;

public enum AlvoEncerramento {
    PACIENTE,
    PROFISSIONAL,
    CONTA;

    public TipoPerfil perfil() {
        return switch (this) {
            case PACIENTE -> TipoPerfil.PACIENTE;
            case PROFISSIONAL -> TipoPerfil.PROFISSIONAL;
            case CONTA -> null;
        };
    }
}
