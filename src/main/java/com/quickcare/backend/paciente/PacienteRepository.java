package com.quickcare.backend.paciente;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    boolean existsByUsuarioId(Long usuarioId);

    Optional<Paciente> findByUsuarioId(Long usuarioId);

}
