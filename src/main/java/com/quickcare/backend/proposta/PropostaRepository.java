package com.quickcare.backend.proposta;

import com.quickcare.backend.solicitacao.StatusSolicitacao;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PropostaRepository extends JpaRepository<Proposta, Long> {

    @EntityGraph(attributePaths = {"profissional", "profissional.usuario"})
    List<Proposta> findBySolicitacaoIdOrderByCriadoEmAsc(Long solicitacaoId);

    List<Proposta> findBySolicitacaoIdAndProfissionalIdOrderByCriadoEmAsc(Long solicitacaoId, Long profissionalId);

    @EntityGraph(attributePaths = {"solicitacao", "solicitacao.paciente", "solicitacao.paciente.usuario"})
    List<Proposta> findByProfissionalIdOrderByCriadoEmDesc(Long profissionalId);

    boolean existsBySolicitacaoIdAndProfissionalIdAndStatusNot(Long solicitacaoId, Long profissionalId, StatusProposta status);

    boolean existsBySolicitacaoIdAndStatus(Long solicitacaoId, StatusProposta status);

    boolean existsByProfissionalIdAndSolicitacaoPacienteIdAndStatus(
            Long profissionalId, Long pacienteId, StatusProposta status);

    boolean existsByProfissionalIdAndSolicitacaoPacienteIdAndStatusAndSolicitacaoStatusIn(
            Long profissionalId, Long pacienteId, StatusProposta status, Collection<StatusSolicitacao> statusSolicitacao);

    long countByProfissionalIdAndStatusAndSolicitacaoStatus(
            Long profissionalId, StatusProposta status, StatusSolicitacao statusSolicitacao);

    boolean existsBySolicitacaoIdAndAtuacaoIdAndStatusIn(
            Long solicitacaoId, Long atuacaoId, Collection<StatusProposta> status);

    List<Proposta> findByAtuacaoIdAndStatusIn(Long atuacaoId, Collection<StatusProposta> status);

    @Query("""
            select p.solicitacao.id as solicitacaoId, p.atuacao.id as atuacaoId
            from Proposta p
            where p.profissional.id = :profissionalId
              and p.status in :status
            """)
    List<PropostaPorAtuacao> findAtuacoesComPropostaDoProfissional(
            @Param("profissionalId") Long profissionalId,
            @Param("status") Collection<StatusProposta> status);

    interface PropostaPorAtuacao {
        Long getSolicitacaoId();

        Long getAtuacaoId();
    }

    List<Proposta> findBySolicitacaoIdAndStatus(Long solicitacaoId, StatusProposta status);

    @EntityGraph(attributePaths = {"profissional", "profissional.usuario", "solicitacao", "solicitacao.paciente",
            "solicitacao.paciente.usuario"})
    List<Proposta> findAllByOrderByCriadoEmDesc();

    List<Proposta> findByProfissionalIdAndSolicitacaoPacienteIdAndStatus(
            Long profissionalId, Long pacienteId, StatusProposta status);

    @Query("""
            select p.solicitacao.id as solicitacaoId, count(p) as total
            from Proposta p
            where p.solicitacao.id in :solicitacaoIds
              and p.status <> :cancelada
            group by p.solicitacao.id
            """)
    List<ContagemPropostas> contarPorSolicitacao(
            @Param("solicitacaoIds") Collection<Long> solicitacaoIds,
            @Param("cancelada") StatusProposta cancelada);

    interface ContagemPropostas {
        Long getSolicitacaoId();

        long getTotal();
    }

}
