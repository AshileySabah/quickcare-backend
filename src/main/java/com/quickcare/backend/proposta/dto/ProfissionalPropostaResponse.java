package com.quickcare.backend.proposta.dto;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.dto.EspecialidadeResponse;
import com.quickcare.backend.profissional.Profissional;

import java.util.Comparator;
import java.util.List;

public record ProfissionalPropostaResponse(
        Long id,
        String nome,
        String avatarUrl,
        CategoriaProfissional categoria,
        String categoriaRotulo,
        List<EspecialidadeResponse> especialidades
) {

    public static ProfissionalPropostaResponse de(Profissional profissional, String avatarUrl) {
        return new ProfissionalPropostaResponse(
                profissional.getId(),
                profissional.getUsuario().getNome(),
                avatarUrl,
                profissional.getCategoria(),
                profissional.getCategoria().getRotulo(),
                profissional.getEspecialidades().stream()
                        .map(especialidade -> new EspecialidadeResponse(
                                especialidade.getId(), especialidade.getNome(), especialidade.getCategoria()))
                        .sorted(Comparator.comparing(EspecialidadeResponse::nome))
                        .toList()
        );
    }

}
