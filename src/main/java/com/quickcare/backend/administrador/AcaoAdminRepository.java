package com.quickcare.backend.administrador;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AcaoAdminRepository extends JpaRepository<AcaoAdmin, Long> {

    @EntityGraph(attributePaths = {"admin"})
    List<AcaoAdmin> findByAlvoTipoAndAlvoIdOrderByCriadoEmDesc(AcaoAdmin.Alvo alvoTipo, Long alvoId);

    @EntityGraph(attributePaths = {"admin"})
    List<AcaoAdmin> findByAlvoTipoAndAlvoIdIn(AcaoAdmin.Alvo alvoTipo, Collection<Long> alvoIds);

}
