package com.quickcare.backend.perfil.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * O que o profissional com proposta aceita vê do paciente. `saude` vem
 * enquanto a solicitação está em curso; `contatosEmergencia`, só depois que o
 * atendimento começou. Sem acesso, os campos nem aparecem no JSON.
 */
public record PerfilPublicoPacienteResponse(
        Long id,
        String nome,
        String avatarUrl,
        int idade,
        String cidade,
        String uf,
        Instant membroDesde,
        @JsonInclude(JsonInclude.Include.NON_NULL) Saude saude,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ContatoEmergenciaResponse> contatosEmergencia,
        /** O bloqueio que quem vê fez desta pessoa, se houver. */
        @JsonInclude(JsonInclude.Include.NON_NULL) Long bloqueioId
) {

    public record Saude(
            String alergias,
            String condicoesSaude,
            String medicamentosEmUso
    ) {
    }

}
