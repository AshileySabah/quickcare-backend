package com.quickcare.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    @Query("""
            SELECT m FROM Mensagem m
            WHERE (m.remetenteId = :usuarioId OR m.destinatarioId = :usuarioId)
              AND m.ativo = true
            ORDER BY m.dataEnvio ASC
            """)
    List<Mensagem> findMensagensAtivasPorUsuario(@Param("usuarioId") Long usuarioId);

}
