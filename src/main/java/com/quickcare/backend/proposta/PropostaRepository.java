package com.quickcare.backend.proposta;

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
