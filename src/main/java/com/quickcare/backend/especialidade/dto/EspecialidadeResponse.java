package com.quickcare.backend.especialidade.dto;

import com.quickcare.backend.especialidade.CategoriaProfissional;

public record EspecialidadeResponse(
        Long id,
        String nome,
        CategoriaProfissional categoria
) {
}
