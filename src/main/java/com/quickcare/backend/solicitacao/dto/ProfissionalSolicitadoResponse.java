package com.quickcare.backend.solicitacao.dto;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.dto.EspecialidadeResponse;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;

import java.util.Comparator;
import java.util.List;

public record ProfissionalSolicitadoResponse(
        CategoriaProfissional categoria,
        String categoriaRotulo,
        List<EspecialidadeResponse> especialidades,
        int quantidade
) {

    public static ProfissionalSolicitadoResponse de(SolicitacaoProfissional profissional) {
        return new ProfissionalSolicitadoResponse(
                profissional.getCategoria(),
                profissional.getCategoria().getRotulo(),
                profissional.getEspecialidades().stream()
                        .map(especialidade -> new EspecialidadeResponse(
                                especialidade.getId(), especialidade.getNome(), especialidade.getCategoria()))
                        .sorted(Comparator.comparing(EspecialidadeResponse::nome))
                        .toList(),
                profissional.getQuantidade()
        );
    }

}
