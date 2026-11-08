package com.quickcare.backend.denuncia;

import com.quickcare.backend.atendimento.LadoAtendimento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DenunciaRepository extends JpaRepository<Denuncia, Long> {

    @EntityGraph(attributePaths = {"paciente", "paciente.usuario", "profissional", "profissional.usuario"})
    List<Denuncia> findByStatusInOrderByCriadoEmAscIdAsc(Collection<StatusDenuncia> status);

    @EntityGraph(attributePaths = {"paciente", "paciente.usuario", "profissional", "profissional.usuario",
            "analisadaPor", "decididaPor"})
    Optional<Denuncia> findWithPartesById(Long id);

    long countByStatus(StatusDenuncia status);

    boolean existsByPacienteIdAndProfissionalIdAndDenuncianteAndStatusIn(
            Long pacienteId, Long profissionalId, LadoAtendimento denunciante, Collection<StatusDenuncia> status);

    long countByProfissionalIdAndDenuncianteAndIdNot(Long profissionalId, LadoAtendimento denunciante, Long id);

    long countByPacienteIdAndDenuncianteAndIdNot(Long pacienteId, LadoAtendimento denunciante, Long id);

}
