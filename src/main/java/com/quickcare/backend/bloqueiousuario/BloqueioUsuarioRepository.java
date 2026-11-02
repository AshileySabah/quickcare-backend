package com.quickcare.backend.bloqueiousuario;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BloqueioUsuarioRepository extends JpaRepository<BloqueioUsuario, Long> {

    boolean existsByPacienteIdAndProfissionalId(Long pacienteId, Long profissionalId);

    Optional<BloqueioUsuario> findByPacienteIdAndProfissionalIdAndBloqueadoPor(
            Long pacienteId, Long profissionalId, AutorBloqueio bloqueadoPor);

    @Query("select distinct b.paciente.id from BloqueioUsuario b where b.profissional.id = :profissionalId")
    Set<Long> findPacienteIdsBloqueadosCom(@Param("profissionalId") Long profissionalId);

    @EntityGraph(attributePaths = {"paciente", "paciente.usuario", "profissional", "profissional.usuario", "criadoPor"})
    List<BloqueioUsuario> findAllByOrderByCriadoEmDesc();

    @EntityGraph(attributePaths = {"profissional", "profissional.usuario"})
    List<BloqueioUsuario> findByPacienteIdAndBloqueadoPorOrderByCriadoEmDesc(Long pacienteId, AutorBloqueio bloqueadoPor);

    @EntityGraph(attributePaths = {"paciente", "paciente.usuario"})
    List<BloqueioUsuario> findByProfissionalIdAndBloqueadoPorOrderByCriadoEmDesc(Long profissionalId, AutorBloqueio bloqueadoPor);

}
