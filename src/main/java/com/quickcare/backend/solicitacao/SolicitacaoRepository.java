package com.quickcare.backend.solicitacao;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long> {

    @EntityGraph(attributePaths = {"profissionais", "profissionais.especialidades"})
    List<Solicitacao> findByPacienteIdOrderByCriadoEmDesc(Long pacienteId);

    /**
     * Solicitações nos status informados com ao menos uma linha de profissionais
     * que o profissional atende: mesma categoria e todas as especialidades da
     * linha entre as dele (espelha {@link SolicitacaoProfissional#atendidaPor}).
     */
    @EntityGraph(attributePaths = {"profissionais", "profissionais.especialidades"})
    @Query("""
            select s from Solicitacao s
            where s.status in :status
              and exists (
                  select 1 from SolicitacaoProfissional p
                  where p.solicitacao = s
                    and p.categoria = :categoria
                    and not exists (
                        select 1 from p.especialidades e
                        where e.id not in :especialidadeIds
                    )
              )
            order by s.criadoEm desc
            """)
    List<Solicitacao> findDisponiveisPara(
            @Param("categoria") CategoriaProfissional categoria,
            @Param("especialidadeIds") Collection<Long> especialidadeIds,
            @Param("status") Collection<StatusSolicitacao> status);

    @EntityGraph(attributePaths = {"profissionais", "profissionais.especialidades"})
    Optional<Solicitacao> findByIdAndPacienteId(Long id, Long pacienteId);

}
