package com.quickcare.backend.atendimento;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {

    @EntityGraph(attributePaths = {"proposta", "proposta.profissional", "proposta.profissional.usuario",
            "solicitacao", "solicitacao.paciente", "solicitacao.paciente.usuario"})
    Optional<Atendimento> findWithPartesById(Long id);

    /** Todos os atendimentos (inclusive os encerrados) destas propostas, para as respostas da API. */
    List<Atendimento> findByPropostaIdInOrderByIdAsc(Collection<Long> propostaIds);

    Optional<Atendimento> findFirstByPropostaIdAndStatusIn(Long propostaId, Collection<StatusAtendimento> status);

    List<Atendimento> findBySolicitacaoIdAndStatusIn(Long solicitacaoId, Collection<StatusAtendimento> status);

    @EntityGraph(attributePaths = {"solicitacao"})
    List<Atendimento> findBySolicitacaoIdInAndStatusIn(Collection<Long> solicitacaoIds, Collection<StatusAtendimento> status);

    boolean existsBySolicitacaoIdAndStatusIn(Long solicitacaoId, Collection<StatusAtendimento> status);

    /** O profissional tem atendimento num destes status com o paciente. */
    boolean existsByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(
            Long profissionalId, Long pacienteId, Collection<StatusAtendimento> status);

    @EntityGraph(attributePaths = {"proposta", "proposta.profissional", "proposta.profissional.usuario",
            "solicitacao", "solicitacao.paciente", "solicitacao.paciente.usuario"})
    List<Atendimento> findByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(
            Long profissionalId, Long pacienteId, Collection<StatusAtendimento> status);

    long countByPropostaProfissionalIdAndStatusIn(Long profissionalId, Collection<StatusAtendimento> status);

    /** Os que já passaram do horário em que o status muda (o job e a leitura processam). */
    @Query("""
            select a.id from Atendimento a
            where (a.status in :antesDoInicio and a.inicio <= :agora)
               or (a.status = com.quickcare.backend.atendimento.StatusAtendimento.EM_ANDAMENTO and a.termino <= :agora)
               or (a.status = com.quickcare.backend.atendimento.StatusAtendimento.FINALIZADO and a.termino <= :limiteConclusao)
            """)
    List<Long> findIdsComTransicaoVencida(
            @Param("antesDoInicio") Collection<StatusAtendimento> antesDoInicio,
            @Param("agora") Instant agora,
            @Param("limiteConclusao") Instant limiteConclusao);

    /** Agendados que entraram numa janela de lembrete ainda não enviada. */
    @Query("""
            select a.id from Atendimento a
            where a.status = com.quickcare.backend.atendimento.StatusAtendimento.AGENDADO
              and ((a.lembrete24hEm is null and a.inicio <= :em24h)
                   or (a.lembrete30minEm is null and a.inicio <= :em30min))
            """)
    List<Long> findIdsComLembreteDevido(@Param("em24h") Instant em24h, @Param("em30min") Instant em30min);

}
