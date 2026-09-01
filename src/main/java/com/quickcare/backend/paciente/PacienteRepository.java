package com.quickcare.backend.paciente;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    boolean existsByUsuarioId(Long usuarioId);

}
