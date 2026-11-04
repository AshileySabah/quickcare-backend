package com.quickcare.backend.bloqueiousuario;

import com.quickcare.backend.usuario.TipoPerfil;

public enum AutorBloqueio {
    PACIENTE,
    PROFISSIONAL,
    ADMIN;

    public static AutorBloqueio de(TipoPerfil perfil) {
        return perfil == TipoPerfil.PACIENTE ? PACIENTE : PROFISSIONAL;
    }
}
