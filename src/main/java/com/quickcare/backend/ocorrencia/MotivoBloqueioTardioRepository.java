package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MotivoBloqueioTardioRepository extends JpaRepository<MotivoBloqueioTardio, Long> {

    long countByStatus(MotivoBloqueioTardio.Status status);

    int countByStatusAndUsuarioIdAndPerfil(MotivoBloqueioTardio.Status status, Long usuarioId, TipoPerfil perfil);

    List<MotivoBloqueioTardio> findByUsuarioIdAndPerfilOrderByCriadoEmDesc(Long usuarioId, TipoPerfil perfil);

    @Modifying(flushAutomatically = true)
    @Query("update MotivoBloqueioTardio m set m.usuario = :novo where m.usuario = :antigo and m.perfil = :perfil")
    int transferir(@Param("antigo") Usuario antigo, @Param("novo") Usuario novo, @Param("perfil") TipoPerfil perfil);

}
