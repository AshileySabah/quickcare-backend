package com.quickcare.backend.perfil.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.profissional.ModalidadeAtendimento;

import java.time.Instant;
import java.util.List;

/**
 * O que outros usuários veem do profissional. Sem telefone, e-mail nem
 * endereço: só cidade e UF. Só profissionais aprovados têm perfil visível,
 * então o registro informado já foi conferido. `contatosEmergencia` só vem
 * para o paciente cujo atendimento com ele já começou.
 */
public record PerfilPublicoProfissionalResponse(
        Long id,
        String nome,
        String avatarUrl,
        CategoriaProfissional categoria,
        String categoriaRotulo,
        List<String> especialidades,
        String siglaRegistro,
        String registroProfissional,
        String registroProfissionalUf,
        ModalidadeAtendimento modalidadeAtendimento,
        String cidade,
        String uf,
        Instant membroDesde,
        long atendimentosConcluidos,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ContatoEmergenciaResponse> contatosEmergencia
) {
}
