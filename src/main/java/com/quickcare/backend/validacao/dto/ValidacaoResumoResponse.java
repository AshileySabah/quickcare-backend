package com.quickcare.backend.validacao.dto;

import com.quickcare.backend.validacao.PerfilValidado;
import com.quickcare.backend.validacao.StatusValidacao;

import java.time.Instant;
import java.util.List;

public record ValidacaoResumoResponse(
        Long id,
        PerfilValidado perfil,
        String nome,
        String email,
        List<String> atuacoes,
        StatusValidacao statusValidacao,
        boolean emRevalidacao,
        Instant cadastradoEm,
        long etapasPendentes
) {
}
