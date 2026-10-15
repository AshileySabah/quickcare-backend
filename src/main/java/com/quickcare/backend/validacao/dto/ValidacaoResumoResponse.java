package com.quickcare.backend.validacao.dto;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.validacao.PerfilValidado;
import com.quickcare.backend.validacao.StatusValidacao;

import java.time.Instant;

public record ValidacaoResumoResponse(
        Long id,
        PerfilValidado perfil,
        String nome,
        String email,
        CategoriaProfissional categoria,
        StatusValidacao statusValidacao,
        Instant cadastradoEm,
        long etapasPendentes
) {
}
