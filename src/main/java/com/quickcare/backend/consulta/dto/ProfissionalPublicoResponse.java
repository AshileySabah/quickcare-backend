package com.quickcare.backend.consulta.dto;

import com.quickcare.backend.atuacao.dto.AtuacaoResumoResponse;
import com.quickcare.backend.avaliacao.dto.ResumoAvaliacoesResponse;
import com.quickcare.backend.profissional.ModalidadeAtendimento;

import java.util.List;

public record ProfissionalPublicoResponse(
        Long id,
        String nome,
        String avatarUrl,
        List<AtuacaoResumoResponse> atuacoes,
        ModalidadeAtendimento modalidadeAtendimento,
        String cidade,
        String uf,
        long atendimentosConcluidos,
        ResumoAvaliacoesResponse avaliacoes
) {
}
