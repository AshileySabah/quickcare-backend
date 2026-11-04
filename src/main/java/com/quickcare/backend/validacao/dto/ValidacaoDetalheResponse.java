package com.quickcare.backend.validacao.dto;

import com.quickcare.backend.validacao.PerfilValidado;
import com.quickcare.backend.validacao.StatusValidacao;

import java.time.Instant;
import java.util.List;

public record ValidacaoDetalheResponse(
        Long id,
        PerfilValidado perfil,
        StatusValidacao statusValidacao,
        boolean emRevalidacao,
        Instant cadastradoEm,
        DadosCadastraisResponse dados,
        List<EtapaValidacaoResponse> etapas
) {
}
