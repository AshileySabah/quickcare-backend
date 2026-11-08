package com.quickcare.backend.denuncia.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.administrador.dto.AcaoAdminResponse;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.banimento.dto.BanimentoResponse;
import com.quickcare.backend.denuncia.ConsequenciaDenuncia;

import java.time.Instant;
import java.util.List;

public record DenunciaDetalheResponse(
        DenunciaResumoResponse resumo,
        String descricao,
        @JsonInclude(JsonInclude.Include.NON_NULL) Anexo anexo,
        @JsonInclude(JsonInclude.Include.NON_NULL) ConsequenciaDenuncia consequencia,
        @JsonInclude(JsonInclude.Include.NON_NULL) String justificativaDecisao,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant analiseIniciadaEm,
        @JsonInclude(JsonInclude.Include.NON_NULL) String analisadaPor,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant decididaEm,
        @JsonInclude(JsonInclude.Include.NON_NULL) String decididaPor,
        @JsonInclude(JsonInclude.Include.NON_NULL) OcorrenciaGerada ocorrencia,
        @JsonInclude(JsonInclude.Include.NON_NULL) BanimentoResponse banimento,
        List<AtendimentoVinculo> atendimentos,
        long outrasDenunciasContraODenunciado,
        List<AcaoAdminResponse> acoes
) {

    public record Anexo(String nomeArquivo, String contentType, String url) {
    }

    public record OcorrenciaGerada(
            Long id,
            boolean anulada,
            @JsonInclude(JsonInclude.Include.NON_NULL) Recurso recurso
    ) {
    }

    public record Recurso(
            Long id,
            String status,
            String texto,
            Instant criadoEm,
            @JsonInclude(JsonInclude.Include.NON_NULL) String justificativaDecisao,
            @JsonInclude(JsonInclude.Include.NON_NULL) Instant decididoEm
    ) {
    }

    public record AtendimentoVinculo(
            Long id,
            Long solicitacaoId,
            String servico,
            StatusAtendimento status,
            Instant inicio,
            Instant termino
    ) {
    }

}
