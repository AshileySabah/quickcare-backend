package com.quickcare.backend.bloqueiousuario;

import com.quickcare.backend.usuario.TipoPerfil;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BloqueioUsuarioRepository extends JpaRepository<BloqueioUsuario, Long> {

    /** Bloqueados entre si, não importa quem bloqueou. */
    boolean existsByPacienteIdAndProfissionalId(Long pacienteId, Long profissionalId);

    Optional<BloqueioUsuario> findByPacienteIdAndProfissionalIdAndBloqueadoPor(
            Long pacienteId, Long profissionalId, TipoPerfil bloqueadoPor);

    /** Pacientes com quem o profissional não tem mais contato (as solicitações deles somem para ele). */
    @Query("select distinct b.paciente.id from BloqueioUsuario b where b.profissional.id = :profissionalId")
    Set<Long> findPacienteIdsBloqueadosCom(@Param("profissionalId") Long profissionalId);

    @EntityGraph(attributePaths = {"profissional", "profissional.usuario"})
    List<BloqueioUsuario> findByPacienteIdAndBloqueadoPorOrderByCriadoEmDesc(Long pacienteId, TipoPerfil bloqueadoPor);

    @EntityGraph(attributePaths = {"paciente", "paciente.usuario"})
    List<BloqueioUsuario> findByProfissionalIdAndBloqueadoPorOrderByCriadoEmDesc(Long profissionalId, TipoPerfil bloqueadoPor);

}
