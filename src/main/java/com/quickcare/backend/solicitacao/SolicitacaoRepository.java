package com.quickcare.backend.solicitacao;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SolicitacaoRepository extends JpaRepository<Solicitacao, Long> {

    @EntityGraph(attributePaths = "especialidade")
    List<Solicitacao> findByPacienteIdOrderByCriadoEmDesc(Long pacienteId);

    @EntityGraph(attributePaths = "especialidade")
    List<Solicitacao> findByEspecialidadeIdInAndStatusInOrderByCriadoEmDesc(
            Collection<Long> especialidadeIds, Collection<StatusSolicitacao> status);

    @EntityGraph(attributePaths = "especialidade")
    Optional<Solicitacao> findByIdAndPacienteId(Long id, Long pacienteId);

}
