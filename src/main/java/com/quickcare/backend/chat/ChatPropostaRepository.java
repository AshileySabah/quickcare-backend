package com.quickcare.backend.chat;

import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.StatusProposta;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Consultas somente leitura sobre as propostas, usadas para saber quem participa de cada
 * conversa. Fica no pacote do chat para não alterar o {@code PropostaRepository}.
 */
public interface ChatPropostaRepository extends Repository<Proposta, Long> {

    /** O usuário é o paciente da solicitação ou um profissional com proposta no status informado. */
    @Query("""
            select count(p) > 0 from Proposta p
            where p.solicitacao.id = :solicitacaoId
              and p.status = :status
              and (p.profissional.usuario.id = :usuarioId
                   or p.solicitacao.paciente.usuario.id = :usuarioId)
            """)
    boolean usuarioParticipa(
            @Param("solicitacaoId") Long solicitacaoId,
            @Param("usuarioId") Long usuarioId,
            @Param("status") StatusProposta status);

    /** Vazio quando a solicitação ainda não tem proposta no status informado. */
    @Query("""
            select distinct p.solicitacao.paciente.usuario.id from Proposta p
            where p.solicitacao.id = :solicitacaoId and p.status = :status
            """)
    List<Long> usuarioIdsDoPaciente(
            @Param("solicitacaoId") Long solicitacaoId,
            @Param("status") StatusProposta status);

    @Query("""
            select p.profissional.usuario.id from Proposta p
            where p.solicitacao.id = :solicitacaoId and p.status = :status
            """)
    List<Long> usuarioIdsDosProfissionais(
            @Param("solicitacaoId") Long solicitacaoId,
            @Param("status") StatusProposta status);

    /** Solicitações em que o usuário conversa, seja como paciente ou como profissional. */
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
