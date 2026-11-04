package com.quickcare.backend.perfil.dto;

import com.quickcare.backend.especialidade.CategoriaProfissional;

import java.util.List;

public record RegistroProfissionalResponse(
        CategoriaProfissional categoria,
        List<Long> especialidadeIds,
        String registroProfissional,
        String registroProfissionalUf
) {
}
