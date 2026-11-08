package com.quickcare.backend.banimento;

import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BanimentoRepository extends JpaRepository<Banimento, Long> {

    @EntityGraph(attributePaths = {"usuario", "aplicadoPor", "encerradoPor", "recursoDecididoPor"})
    List<Banimento> findByUsuarioIdAndPerfilOrderByInicioDescIdDesc(Long usuarioId, TipoPerfil perfil);

    @Query("""
            select b from Banimento b
            where b.usuario.id = :usuarioId and b.perfil = :perfil
              and b.encerradoEm is null and (b.fim is null or b.fim > :agora)
            """)
    Optional<Banimento> findAtivo(@Param("usuarioId") Long usuarioId, @Param("perfil") TipoPerfil perfil,
                                  @Param("agora") Instant agora);

    @Query("""
            select b.id from Banimento b
            where b.encerradoEm is null and b.fim is not null and b.fim <= :agora
              and ((b.perfil = com.quickcare.backend.usuario.TipoPerfil.PACIENTE
                    and exists (select pa.id from Paciente pa where pa.usuario = b.usuario))
                or (b.perfil = com.quickcare.backend.usuario.TipoPerfil.PROFISSIONAL
                    and exists (select pr.id from Profissional pr where pr.usuario = b.usuario)))
            """)
    List<Long> findIdsVencidos(@Param("agora") Instant agora);

    Optional<Banimento> findFirstByDenunciaIdOrderByIdDesc(Long denunciaId);

    long countByRecursoStatus(Banimento.StatusRecurso status);

    long countByRecursoStatusAndUsuarioIdAndPerfil(Banimento.StatusRecurso status, Long usuarioId, TipoPerfil perfil);

    @Modifying(flushAutomatically = true)
    @Query("update Banimento b set b.usuario = :novo where b.usuario = :antigo and b.perfil = :perfil")
    int transferir(@Param("antigo") Usuario antigo, @Param("novo") Usuario novo, @Param("perfil") TipoPerfil perfil);

}
