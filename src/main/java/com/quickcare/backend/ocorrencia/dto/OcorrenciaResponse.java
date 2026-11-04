package com.quickcare.backend.ocorrencia.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.ocorrencia.Bloqueio;
import com.quickcare.backend.ocorrencia.MotivoOcorrencia;
import com.quickcare.backend.ocorrencia.Ocorrencia;
import com.quickcare.backend.ocorrencia.RecursoOcorrencia;
import com.quickcare.backend.ocorrencia.RegrasBloqueio;
import com.quickcare.backend.usuario.Usuario;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OcorrenciaResponse(
        Long id,
        MotivoOcorrencia motivo,
        Instant criadoEm,
        Long solicitacaoId,
        Long atendimentoId,
        boolean anulada,
        Instant anuladaEm,
        String justificativa,
        String criadoPor,
        String justificativaAnulacao,
        String anuladaPor,
        boolean podeRecorrer,
        Recurso recurso,
        boolean jaGerouBloqueio
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Recurso(
            Long id,
            RecursoOcorrencia.Status status,
            String texto,
            Instant criadoEm,
            Instant decididoEm,
            String justificativaDecisao,
            String decididoPor
    ) {

        static Recurso de(RecursoOcorrencia recurso, boolean paraOAdmin) {
            if (recurso == null) {
                return null;
            }
            return new Recurso(recurso.getId(), recurso.getStatus(), recurso.getTexto(), recurso.getCriadoEm(),
                    recurso.getDecididoEm(), recurso.getJustificativaDecisao(),
                    paraOAdmin ? nome(recurso.getDecididoPor()) : null);
        }
    }

    public static OcorrenciaResponse paraOPerfil(Ocorrencia ocorrencia, Bloqueio corte) {
        return new OcorrenciaResponse(ocorrencia.getId(), ocorrencia.getMotivo(), ocorrencia.getCriadoEm(),
                ocorrencia.getSolicitacao() == null ? null : ocorrencia.getSolicitacao().getId(),
                ocorrencia.getAtendimento() == null ? null : ocorrencia.getAtendimento().getId(),
                ocorrencia.anulada(), ocorrencia.getAnuladaEm(),
                ocorrencia.getJustificativa(), null, ocorrencia.getJustificativaAnulacao(), null,
                ocorrencia.podeRecorrer(), Recurso.de(ocorrencia.getRecurso(), false),
                !ocorrencia.anulada() && RegrasBloqueio.jaGerouBloqueio(ocorrencia.getCriadoEm(), corte));
    }

    public static OcorrenciaResponse paraOAdmin(Ocorrencia ocorrencia, Bloqueio corte) {
        return new OcorrenciaResponse(ocorrencia.getId(), ocorrencia.getMotivo(), ocorrencia.getCriadoEm(),
                ocorrencia.getSolicitacao() == null ? null : ocorrencia.getSolicitacao().getId(),
                ocorrencia.getAtendimento() == null ? null : ocorrencia.getAtendimento().getId(),
                ocorrencia.anulada(), ocorrencia.getAnuladaEm(),
                ocorrencia.getJustificativa(), nome(ocorrencia.getCriadoPor()),
                ocorrencia.getJustificativaAnulacao(), nome(ocorrencia.getAnuladaPor()),
                false, Recurso.de(ocorrencia.getRecurso(), true),
                !ocorrencia.anulada() && RegrasBloqueio.jaGerouBloqueio(ocorrencia.getCriadoEm(), corte));
    }

    private static String nome(Usuario usuario) {
        return usuario == null ? null : usuario.getNome();
    }

}
