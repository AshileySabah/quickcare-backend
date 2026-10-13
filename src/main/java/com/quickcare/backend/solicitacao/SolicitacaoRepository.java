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
     * Pré-filtro no banco: solicitações nos status e modalidades informados com
     * ao menos uma linha com vaga aberta que o profissional atende (mesma
     * categoria e todas as especialidades da linha entre as dele). O raio de
     * atendimento é conferido depois, em {@link Solicitacao#atendidaPor}.
     */
    @EntityGraph(attributePaths = {"profissionais", "profissionais.especialidades"})
    @Query("""
            select s from Solicitacao s
            where s.status in :status
              and s.modalidade in :modalidades
              and exists (
                  select 1 from SolicitacaoProfissional p
                  where p.solicitacao = s
                    and p.categoria = :categoria
                    and p.vagasPreenchidas < p.quantidade
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
            @Param("modalidades") Collection<ModalidadeSolicitacao> modalidades,
            @Param("status") Collection<StatusSolicitacao> status);

    @EntityGraph(attributePaths = {"profissionais", "profissionais.especialidades"})
    Optional<Solicitacao> findByIdAndPacienteId(Long id, Long pacienteId);

}
