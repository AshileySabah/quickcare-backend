package com.quickcare.backend.chat;

import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.StatusProposta;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Consultas somente leitura sobre propostas e solicitações, usadas para saber quem participa de
 * cada conversa. Fica no pacote do chat para não alterar o {@code PropostaRepository}.
 */
public interface ChatPropostaRepository extends Repository<Proposta, Long> {

    /** O paciente é sempre o dono da solicitação, independentemente do status das propostas. */
    @Query("select s.paciente.usuario.id from Solicitacao s where s.id = :solicitacaoId")
    Optional<Long> usuarioIdDoPaciente(@Param("solicitacaoId") Long solicitacaoId);

    @Query("""
            select distinct p.profissional.usuario.id from Proposta p
            where p.solicitacao.id = :solicitacaoId and p.status = :status
            """)
    List<Long> usuarioIdsDosProfissionais(
            @Param("solicitacaoId") Long solicitacaoId,
            @Param("status") StatusProposta status);

    /**
     * Quando a proposta do profissional mudou pela última vez. Usado como instante de saída
     * quando ela deixa de estar aceita (cancelada, recusada, devolvida para novo aceite...).
     */
    @Query("""
            select max(p.atualizadoEm) from Proposta p
            where p.solicitacao.id = :solicitacaoId and p.profissional.usuario.id = :usuarioId
            """)
    Instant ultimaAtualizacaoDaProposta(
            @Param("solicitacaoId") Long solicitacaoId,
            @Param("usuarioId") Long usuarioId);

    /** Solicitações com proposta no status informado em que o usuário é paciente ou profissional. */
    @Query("""
            select distinct p.solicitacao.id from Proposta p
            where p.status = :status
              and (p.profissional.usuario.id = :usuarioId
                   or p.solicitacao.paciente.usuario.id = :usuarioId)
            """)
    List<Long> solicitacaoIdsComChat(
            @Param("usuarioId") Long usuarioId,
            @Param("status") StatusProposta status);

}
