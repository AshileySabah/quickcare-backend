package com.quickcare.backend.seguranca;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CodigoSegurancaRepository extends JpaRepository<CodigoSeguranca, Long> {

    Optional<CodigoSeguranca> findByUsuarioIdAndFinalidade(Long usuarioId, FinalidadeCodigo finalidade);

    @EntityGraph(attributePaths = {"usuario"})
    Optional<CodigoSeguranca> findByCodigoHashAndFinalidade(String codigoHash, FinalidadeCodigo finalidade);

    @Modifying(flushAutomatically = true)
    @Query("delete from CodigoSeguranca c where c.usuario.id = :usuarioId and c.finalidade = :finalidade")
    void excluir(@Param("usuarioId") Long usuarioId, @Param("finalidade") FinalidadeCodigo finalidade);

    @Modifying(flushAutomatically = true)
    @Query("delete from CodigoSeguranca c where c.usuario.id = :usuarioId")
    void excluirDoUsuario(@Param("usuarioId") Long usuarioId);

}
