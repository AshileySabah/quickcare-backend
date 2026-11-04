package com.quickcare.backend.problema;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProblemaRepository extends JpaRepository<ProblemaAtendimento, Long> {

    @EntityGraph(attributePaths = {"anexos", "atendimento", "atendimento.proposta", "atendimento.proposta.profissional",
            "atendimento.proposta.profissional.usuario", "atendimento.solicitacao", "atendimento.solicitacao.paciente",
            "atendimento.solicitacao.paciente.usuario"})
    Optional<ProblemaAtendimento> findWithPartesById(Long id);

    Optional<ProblemaAtendimento> findByAtendimentoId(Long atendimentoId);

    @EntityGraph(attributePaths = {"anexos"})
    List<ProblemaAtendimento> findByAtendimentoIdIn(Collection<Long> atendimentoIds);

    boolean existsByAtendimentoId(Long atendimentoId);

    @EntityGraph(attributePaths = {"atendimento", "atendimento.proposta", "atendimento.proposta.profissional",
            "atendimento.proposta.profissional.usuario", "atendimento.solicitacao", "atendimento.solicitacao.paciente",
            "atendimento.solicitacao.paciente.usuario"})
    List<ProblemaAtendimento> findByStatusInOrderByCriadoEmAsc(Collection<StatusProblema> status);

    long countByStatus(StatusProblema status);

    @Query("""
            select p.id from ProblemaAtendimento p
            where p.status = com.quickcare.backend.problema.StatusProblema.AGUARDANDO_RESPOSTA
              and p.prazoResposta <= :agora
            """)
    List<Long> findIdsComPrazoVencido(@Param("agora") Instant agora);

}
