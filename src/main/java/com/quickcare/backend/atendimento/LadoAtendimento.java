package com.quickcare.backend.atendimento;

/** Quem fez algo no atendimento; SISTEMA para o que acontece sozinho (prazo vencido). */
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
