package com.quickcare.backend.proposta.dto;

import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.dto.EspecialidadeResponse;
import com.quickcare.backend.proposta.Proposta;

import java.util.List;

public record ProfissionalPropostaResponse(
        Long id,
        String nome,
        String avatarUrl,
        Long atuacaoId,
        CategoriaProfissional categoria,
        String categoriaRotulo,
        String atuacaoRotulo,
        List<EspecialidadeResponse> especialidades,
        boolean perfilIndisponivel
) {

    public static ProfissionalPropostaResponse de(Proposta proposta, String avatarUrl) {
        return de(proposta, proposta.getProfissional().getUsuario().getNome(), avatarUrl, false);
    }

    public static ProfissionalPropostaResponse indisponivel(Proposta proposta) {
        return de(proposta, null, null, true);
    }

    private static ProfissionalPropostaResponse de(Proposta proposta, String nome, String avatarUrl, boolean indisponivel) {
        Atuacao atuacao = proposta.getAtuacao();
        return new ProfissionalPropostaResponse(
                proposta.getProfissional().getId(),
                nome,
                avatarUrl,
                atuacao.getId(),
                atuacao.getCategoria(),
                atuacao.getCategoria().getRotulo(),
                atuacao.rotulo(),
                atuacao.especialidadesOrdenadas().stream()
                        .map(especialidade -> new EspecialidadeResponse(
                                especialidade.getId(), especialidade.getNome(), especialidade.getCategoria()))
                        .toList(),
                indisponivel
        );
    }

}
