package com.quickcare.backend.problema.dto;

import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.problema.DecisaoProblema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * O admin decide se aconteceu e quem leva ocorrência (nenhum, um ou os dois);
 * as ocorrências que o relato tinha gerado são substituídas por estas.
 */
public record DecisaoDisputaRequest(
        @NotNull(message = "Informe se o atendimento foi realizado.") DecisaoProblema decisao,
        Set<LadoAtendimento> ocorrenciaPara,
        @NotBlank(message = "Escreva a justificativa.")
        @Size(max = 2000, message = "A justificativa deve ter no máximo 2000 caracteres.") String justificativa
) {

    public Set<LadoAtendimento> ocorrenciaParaOuNenhum() {
        return ocorrenciaPara == null ? Set.of() : ocorrenciaPara;
    }

}
