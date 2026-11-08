package com.quickcare.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    List<Mensagem> findBySolicitacaoIdAndAtivoTrueOrderByDataEnvioAsc(Long solicitacaoId);

    /** A mensagem ativa mais recente de cada solicitação informada (as sem mensagem não aparecem). */
    @Query("""
            select m from Mensagem m
            where m.ativo = true
              and m.solicitacaoId in :solicitacaoIds
              and m.id = (select max(m2.id) from Mensagem m2
                          where m2.solicitacaoId = m.solicitacaoId and m2.ativo = true)
            """)
    List<Mensagem> findUltimasPorSolicitacao(@Param("solicitacaoIds") Collection<Long> solicitacaoIds);

}
