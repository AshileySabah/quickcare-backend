package com.quickcare.backend.proposta.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.proposta.MotivoReaceite;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.usuario.Usuario;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Function;

public record PropostaResponse(
        Long id,
        SolicitacaoResumoResponse solicitacao,
        ProfissionalPropostaResponse profissional,
        BigDecimal valor,
        String abordagem,
        LocalDate prazo,
        StatusProposta status,
        @JsonInclude(JsonInclude.Include.NON_NULL) MotivoReaceite motivoReaceite,
        Instant criadoEm,
        Instant atualizadoEm
) {

    /** {@code avatarDe}: URL da foto de cada lado (paciente e profissional), nula para quem não tem. */
    public static PropostaResponse de(Proposta proposta, Function<Usuario, String> avatarDe) {
        boolean aceita = proposta.getStatus() == StatusProposta.ACEITA;
        return new PropostaResponse(
                proposta.getId(),
                SolicitacaoResumoResponse.de(proposta.getSolicitacao(), aceita,
                        aceita ? avatarDe.apply(proposta.getSolicitacao().getPaciente().getUsuario()) : null),
                ProfissionalPropostaResponse.de(proposta.getProfissional(),
                        avatarDe.apply(proposta.getProfissional().getUsuario())),
                proposta.getValor(),
                proposta.getAbordagem(),
                proposta.getPrazo(),
                proposta.getStatus(),
                proposta.getMotivoReaceite(),
                proposta.getCriadoEm(),
                proposta.getAtualizadoEm()
        );
    }

}
