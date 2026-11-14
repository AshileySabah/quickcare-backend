package com.quickcare.backend.documento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentoRepository extends JpaRepository<Documento, Long> {

    @Query("""
            select d.storageKey from Documento d left join d.paciente pa left join d.profissional pr
            where pa.usuario.id = :usuarioId or pr.usuario.id = :usuarioId
            """)
    List<String> findChavesDoUsuario(@Param("usuarioId") Long usuarioId);

}
