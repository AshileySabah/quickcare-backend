package com.quickcare.backend.proposta;

import com.quickcare.backend.solicitacao.StatusSolicitacao;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface PropostaRepository extends JpaRepository<Proposta, Long> {

    @EntityGraph(attributePaths = {"profissional", "profissional.usuario"})
    List<Proposta> findBySolicitacaoIdOrderByCriadoEmAsc(Long solicitacaoId);

    List<Proposta> findBySolicitacaoIdAndProfissionalIdOrderByCriadoEmAsc(Long solicitacaoId, Long profissionalId);

    @EntityGraph(attributePaths = {"solicitacao", "solicitacao.paciente", "solicitacao.paciente.usuario"})
    List<Proposta> findByProfissionalIdOrderByCriadoEmDesc(Long profissionalId);

    boolean existsBySolicitacaoIdAndProfissionalIdAndStatusNot(Long solicitacaoId, Long profissionalId, StatusProposta status);

    boolean existsBySolicitacaoIdAndProfissionalIdAndStatusIn(
            Long solicitacaoId, Long profissionalId, Collection<StatusProposta> status);

    boolean existsBySolicitacaoIdAndStatus(Long solicitacaoId, StatusProposta status);

    /** O profissional tem proposta com o status informado em alguma solicitação do paciente. */
    boolean existsByProfissionalIdAndSolicitacaoPacienteIdAndStatus(
            Long profissionalId, Long pacienteId, StatusProposta status);

    /** Proposta do profissional aceita numa solicitação do paciente ainda em curso. */
    boolean existsByProfissionalIdAndSolicitacaoPacienteIdAndStatusAndSolicitacaoStatusIn(
            Long profissionalId, Long pacienteId, StatusProposta status, Collection<StatusSolicitacao> statusSolicitacao);

    long countByProfissionalIdAndStatusAndSolicitacaoStatus(
            Long profissionalId, StatusProposta status, StatusSolicitacao statusSolicitacao);

    /** Solicitações em que o profissional tem proposta com um destes status. */
    @Query("""
            select distinct p.solicitacao.id
            from Proposta p
            where p.profissional.id = :profissionalId
              and p.status in :status
            """)
    Set<Long> findSolicitacaoIdsComPropostaDoProfissional(
            @Param("profissionalId") Long profissionalId,
            @Param("status") Collection<StatusProposta> status);

    List<Proposta> findBySolicitacaoIdAndStatus(Long solicitacaoId, StatusProposta status);

    /** Propostas não canceladas por solicitação — o que o paciente vê como "propostas recebidas". */
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
