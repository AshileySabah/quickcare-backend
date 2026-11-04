package com.quickcare.backend.problema.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.problema.DecisaoProblema;
import com.quickcare.backend.problema.MotivoProblema;
import com.quickcare.backend.problema.ProblemaAnexo;
import com.quickcare.backend.problema.ProblemaAtendimento;
import com.quickcare.backend.problema.StatusProblema;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemaResponse(
        Long id,
        StatusProblema status,
        LadoAtendimento relatadoPor,
        MotivoProblema motivo,
        String descricao,
        Instant criadoEm,
        Instant prazoResposta,
        String contestacao,
        Instant contestadoEm,
        DecisaoProblema decisao,
        String decisaoJustificativa,
        Instant decididoEm,
        List<Anexo> anexos
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Anexo(Long id, LadoAtendimento lado, String nomeArquivo, String contentType, String url) {
    }

    public static ProblemaResponse de(ProblemaAtendimento problema) {
        return de(problema, anexo -> null);
    }

    public static ProblemaResponse de(ProblemaAtendimento problema, Function<ProblemaAnexo, String> url) {
        return new ProblemaResponse(
                problema.getId(),
                problema.getStatus(),
                problema.getRelatadoPor(),
                problema.getMotivo(),
                problema.getDescricao(),
                problema.getCriadoEm(),
                problema.getPrazoResposta(),
                problema.getContestacao(),
                problema.getContestadoEm(),
                problema.getDecisao(),
                problema.getDecisaoJustificativa(),
                problema.getDecididoEm(),
                problema.getAnexos().stream()
                        .map(anexo -> new Anexo(anexo.getId(), anexo.getLado(), anexo.getNomeArquivo(),
                                anexo.getContentType(), url.apply(anexo)))
                        .toList());
    }

}
