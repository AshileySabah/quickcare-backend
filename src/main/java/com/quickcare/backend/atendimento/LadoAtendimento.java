package com.quickcare.backend.atendimento;

public enum LadoAtendimento {
    PACIENTE,
    PROFISSIONAL,
    SISTEMA;

    public LadoAtendimento outro() {
        return switch (this) {
            case PACIENTE -> PROFISSIONAL;
            case PROFISSIONAL -> PACIENTE;
            case SISTEMA -> SISTEMA;
        };
    }
}
