package com.quickcare.backend.proposta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.dto.AtendimentoResponse;
import com.quickcare.backend.proposta.MotivoReaceite;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.usuario.Usuario;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.function.Function;

public record PropostaResponse(
        Long id,
        SolicitacaoResumoResponse solicitacao,
        ProfissionalPropostaResponse profissional,
        BigDecimal valor,
        String abordagem,
        Instant inicio,
        Instant termino,
        StatusProposta status,
        @JsonInclude(JsonInclude.Include.NON_NULL) MotivoReaceite motivoReaceite,
        /** O atendimento mais recente da proposta (o ativo, ou o último encerrado); nulo se nunca foi aceita. */
        @JsonInclude(JsonInclude.Include.NON_NULL) AtendimentoResponse atendimento,
        Instant criadoEm,
        Instant atualizadoEm
) {

    /** {@code avatarDe}: URL da foto de cada lado (paciente e profissional), nula para quem não tem. */
    public static PropostaResponse de(
            Proposta proposta, Atendimento atendimento, Instant agora, Function<Usuario, String> avatarDe) {
        boolean aceita = proposta.getStatus() == StatusProposta.ACEITA;
        return new PropostaResponse(
                proposta.getId(),
                SolicitacaoResumoResponse.de(proposta.getSolicitacao(), aceita,
                        aceita ? avatarDe.apply(proposta.getSolicitacao().getPaciente().getUsuario()) : null),
                ProfissionalPropostaResponse.de(proposta.getProfissional(),
                        avatarDe.apply(proposta.getProfissional().getUsuario())),
                proposta.getValor(),
                proposta.getAbordagem(),
                proposta.getInicio(),
                proposta.getTermino(),
                proposta.getStatus(),
                proposta.getMotivoReaceite(),
                atendimento == null ? null : AtendimentoResponse.de(atendimento, agora),
                proposta.getCriadoEm(),
                proposta.getAtualizadoEm()
        );
    }

}
