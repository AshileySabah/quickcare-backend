package com.quickcare.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversaProfissionalRepository extends JpaRepository<ConversaProfissional, Long> {

    List<ConversaProfissional> findBySolicitacaoId(Long solicitacaoId);

    /** Solicitações em que o usuário já foi profissional da conversa (ativo ou não). */
    @Query("select c.solicitacaoId from ConversaProfissional c where c.usuarioId = :usuarioId")
    List<Long> solicitacaoIdsComoProfissional(@Param("usuarioId") Long usuarioId);

    /** Solicitações do paciente que já tiveram conversa, mesmo que todos os profissionais tenham saído. */
    @Query("""
            select distinct c.solicitacaoId from ConversaProfissional c
            where c.solicitacaoId in (select s.id from Solicitacao s where s.paciente.usuario.id = :usuarioId)
            """)
    List<Long> solicitacaoIdsComoPaciente(@Param("usuarioId") Long usuarioId);

}
