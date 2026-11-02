package com.quickcare.backend.notificacao;

import com.quickcare.backend.usuario.TipoPerfil;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByUsuarioIdAndPerfilOrderByCriadoEmDescIdDesc(Long usuarioId, TipoPerfil perfil, Pageable pagina);

    Optional<Notificacao> findByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioIdAndPerfilAndLidaEmIsNull(Long usuarioId, TipoPerfil perfil);

    @Modifying
    @Query("""
            update Notificacao n set n.lidaEm = :agora
            where n.usuario.id = :usuarioId and n.perfil = :perfil and n.lidaEm is null
            """)
    int marcarTodasComoLidas(
            @Param("usuarioId") Long usuarioId,
            @Param("perfil") TipoPerfil perfil,
            @Param("agora") Instant agora);

}
