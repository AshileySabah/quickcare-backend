package com.quickcare.backend.consulta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SolicitacaoPublicaResponse(
        Long id,
        ModalidadeSolicitacao modalidade,
        @JsonInclude(JsonInclude.Include.NON_NULL) String cidade,
        @JsonInclude(JsonInclude.Include.NON_NULL) String uf,
        LocalDate prazoDesejado,
        Instant criadoEm,
        List<Vaga> profissionais
) {

    public record Vaga(
            CategoriaProfissional categoria,
            String categoriaRotulo,
            List<String> especialidades,
            int quantidade,
            int vagasAbertas
    ) {
    }

}
