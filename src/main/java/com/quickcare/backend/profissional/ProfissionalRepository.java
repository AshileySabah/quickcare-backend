package com.quickcare.backend.profissional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {

    boolean existsByUsuarioId(Long usuarioId);

    Optional<Profissional> findByUsuarioId(Long usuarioId);

}
