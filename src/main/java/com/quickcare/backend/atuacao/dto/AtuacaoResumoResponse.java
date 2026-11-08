package com.quickcare.backend.atuacao.dto;

import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.dto.EspecialidadeResponse;

import java.util.List;

public record AtuacaoResumoResponse(
        Long id,
        CategoriaProfissional categoria,
        String categoriaRotulo,
        String rotulo,
        String siglaRegistro,
        List<EspecialidadeResponse> especialidades,
        String registroProfissional,
        String registroProfissionalUf
) {

    public static AtuacaoResumoResponse de(Atuacao atuacao) {
        return new AtuacaoResumoResponse(
                atuacao.getId(),
                atuacao.getCategoria(),
                atuacao.getCategoria().getRotulo(),
                atuacao.rotulo(),
                atuacao.getCategoria().getSiglaRegistro(),
                atuacao.especialidadesOrdenadas().stream()
                        .map(especialidade -> new EspecialidadeResponse(
                                especialidade.getId(), especialidade.getNome(), especialidade.getCategoria()))
                        .toList(),
                atuacao.getRegistroProfissional(),
                atuacao.getRegistroProfissionalUf()
        );
    }

}
