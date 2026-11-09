package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {

    boolean existsByUsuarioIdAndSolicitacaoIdAndMotivo(Long usuarioId, Long solicitacaoId, MotivoOcorrencia motivo);

    @EntityGraph(attributePaths = {"solicitacao", "criadoPor", "anuladaPor"})
    List<Ocorrencia> findByUsuarioIdAndPerfilOrderByCriadoEmDesc(Long usuarioId, TipoPerfil perfil);

    @Query("""
            select o.criadoEm from Ocorrencia o
            where o.usuario.id = :usuarioId and o.perfil = :perfil and o.anuladaEm is null
            order by o.criadoEm
            """)
    List<Instant> findMomentosValidos(@Param("usuarioId") Long usuarioId, @Param("perfil") TipoPerfil perfil);

    List<Ocorrencia> findByProblemaIdAndAnuladaEmIsNull(Long problemaId);

    @Query("""
            select o from Ocorrencia o
            where o.anuladaEm is null and o.criadoEm > :desde
            """)
    List<Ocorrencia> findValidasDesde(@Param("desde") Instant desde);

    @Modifying(flushAutomatically = true)
    @Query("update Ocorrencia o set o.usuario = :novo where o.usuario = :antigo and o.perfil = :perfil")
    int transferir(@Param("antigo") Usuario antigo, @Param("novo") Usuario novo, @Param("perfil") TipoPerfil perfil);

}
