package com.quickcare.backend.ocorrencia;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {

    boolean existsByUsuarioIdAndSolicitacaoIdAndMotivo(Long usuarioId, Long solicitacaoId, MotivoOcorrencia motivo);

}
