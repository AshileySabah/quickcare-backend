package com.quickcare.backend.solicitacao.dto;

import com.quickcare.backend.administrador.dto.ParteResponse;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
import com.quickcare.backend.solicitacao.StatusSolicitacao;

import java.time.Instant;
import java.time.LocalDate;

public record SolicitacaoAdminResumoResponse(
        Long id,
        StatusSolicitacao status,
        ParteResponse paciente,
        String servico,
        ModalidadeSolicitacao modalidade,
        String cidade,
        String uf,
        LocalDate prazoDesejado,
        int vagas,
        int vagasPreenchidas,
        Instant criadoEm
) {

    public static SolicitacaoAdminResumoResponse de(Solicitacao solicitacao) {
        return new SolicitacaoAdminResumoResponse(
                solicitacao.getId(),
                solicitacao.getStatus(),
                ParteResponse.de(solicitacao.getPaciente().getUsuario()),
                Notificador.servico(solicitacao),
                solicitacao.getModalidade(),
                solicitacao.getCidade(),
                solicitacao.getUf(),
                solicitacao.getPrazoDesejado(),
                solicitacao.getProfissionais().stream().mapToInt(SolicitacaoProfissional::getQuantidade).sum(),
                solicitacao.getProfissionais().stream().mapToInt(SolicitacaoProfissional::getVagasPreenchidas).sum(),
                solicitacao.getCriadoEm());
    }

}
