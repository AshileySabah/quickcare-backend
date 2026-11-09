package com.quickcare.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    List<Mensagem> findBySolicitacaoIdAndProfissionalUsuarioIdIsNullAndAtivoTrueOrderByDataEnvioAsc(Long solicitacaoId);

    List<Mensagem> findBySolicitacaoIdAndProfissionalUsuarioIdAndAtivoTrueOrderByDataEnvioAsc(
            Long solicitacaoId, Long profissionalUsuarioId);

    /** Mensagens ativas de um canal, da mais antiga para a mais nova: grupo se o profissional for nulo. */
    default List<Mensagem> listarDoCanal(Long solicitacaoId, Long profissionalUsuarioId) {
        return profissionalUsuarioId == null
                ? findBySolicitacaoIdAndProfissionalUsuarioIdIsNullAndAtivoTrueOrderByDataEnvioAsc(solicitacaoId)
                : findBySolicitacaoIdAndProfissionalUsuarioIdAndAtivoTrueOrderByDataEnvioAsc(
                        solicitacaoId, profissionalUsuarioId);
    }

    /** A mensagem ativa mais recente de cada canal das solicitações informadas (canais sem mensagem não aparecem). */
    @Query("""
            select m from Mensagem m
            where m.ativo = true
              and m.solicitacaoId in :solicitacaoIds
              and m.id = (select max(m2.id) from Mensagem m2
                          where m2.solicitacaoId = m.solicitacaoId and m2.ativo = true
                            and ((m2.profissionalUsuarioId is null and m.profissionalUsuarioId is null)
                                 or m2.profissionalUsuarioId = m.profissionalUsuarioId))
            """)
    List<Mensagem> findUltimasPorCanal(@Param("solicitacaoIds") Collection<Long> solicitacaoIds);

}
