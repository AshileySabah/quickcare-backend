package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.usuario.TipoPerfil;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BloqueioRepository extends JpaRepository<Bloqueio, Long> {

    Optional<Bloqueio> findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(Long usuarioId, TipoPerfil perfil);

    Optional<Bloqueio> findFirstByUsuarioIdAndPerfilAndDesfeitoFalseAndInicioBeforeOrderByInicioDesc(
            Long usuarioId, TipoPerfil perfil, Instant inicio);

    @EntityGraph(attributePaths = {"removidoPor"})
    List<Bloqueio> findByUsuarioIdAndPerfilOrderByInicioDesc(Long usuarioId, TipoPerfil perfil);

    @Query("""
            select b from Bloqueio b
            where b.removidoEm is null and b.inicio <= :agora and b.fim > :agora
            """)
    List<Bloqueio> findAtivos(@Param("agora") Instant agora);

}
