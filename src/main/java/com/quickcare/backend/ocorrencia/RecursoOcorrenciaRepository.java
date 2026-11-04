package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.usuario.TipoPerfil;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecursoOcorrenciaRepository extends JpaRepository<RecursoOcorrencia, Long> {

    long countByStatus(RecursoOcorrencia.Status status);

    int countByStatusAndOcorrenciaUsuarioIdAndOcorrenciaPerfil(
            RecursoOcorrencia.Status status, Long usuarioId, TipoPerfil perfil);

}
