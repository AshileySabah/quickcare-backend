package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.usuario.TipoPerfil;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MotivoBloqueioTardioRepository extends JpaRepository<MotivoBloqueioTardio, Long> {

    long countByStatus(MotivoBloqueioTardio.Status status);

    int countByStatusAndUsuarioIdAndPerfil(MotivoBloqueioTardio.Status status, Long usuarioId, TipoPerfil perfil);

    List<MotivoBloqueioTardio> findByUsuarioIdAndPerfilOrderByCriadoEmDesc(Long usuarioId, TipoPerfil perfil);

}
