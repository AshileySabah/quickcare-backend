package com.quickcare.backend.ocorrencia.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.ocorrencia.Bloqueio;
import com.quickcare.backend.ocorrencia.Ocorrencia;
import com.quickcare.backend.ocorrencia.RecursoOcorrencia;
import com.quickcare.backend.ocorrencia.RegrasBloqueio;

import java.util.List;

/** O que o perfil vê sobre as próprias ocorrências. */
public record ResumoOcorrenciasResponse(
        int contando,
        int limite,
        int janelaDias,
        @JsonInclude(JsonInclude.Include.NON_NULL) BloqueioResponse bloqueio,
        List<OcorrenciaResponse> ocorrencias
) {

    /** {@code corte}: o último bloqueio que já terminou, para marcar as que não contam mais. */
    public static ResumoOcorrenciasResponse de(int contando, Bloqueio ativo, Bloqueio corte, List<Ocorrencia> ocorrencias) {
        return new ResumoOcorrenciasResponse(contando, RegrasBloqueio.LIMITE,
                (int) RegrasBloqueio.JANELA_CONTAGEM.toDays(),
                ativo == null ? null : BloqueioResponse.de(ativo, ativo.getInicio()),
                // Recurso aceito: a ocorrência some da lista (anulada pelo admin direto, aparece riscada).
                ocorrencias.stream()
                        .filter(ocorrencia -> ocorrencia.getRecurso() == null
                                || ocorrencia.getRecurso().getStatus() != RecursoOcorrencia.Status.ANULADA)
                        .map(ocorrencia -> OcorrenciaResponse.paraOPerfil(ocorrencia, corte))
                        .toList());
    }

}
