package com.quickcare.backend.especialidade.dto;

import com.quickcare.backend.especialidade.CategoriaProfissional;

public record CategoriaProfissionalResponse(
        CategoriaProfissional valor,
        String rotulo,
        String siglaRegistro
) {
}
