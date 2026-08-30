package com.quickcare.backend.profissional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {

    boolean existsByUsuarioId(Long usuarioId);

}
