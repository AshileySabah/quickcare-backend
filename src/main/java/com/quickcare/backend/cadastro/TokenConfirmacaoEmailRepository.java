package com.quickcare.backend.cadastro;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TokenConfirmacaoEmailRepository extends JpaRepository<TokenConfirmacaoEmail, Long> {

    @EntityGraph(attributePaths = {"usuario"})
    Optional<TokenConfirmacaoEmail> findByTokenHash(String tokenHash);

    Optional<TokenConfirmacaoEmail> findByUsuarioId(Long usuarioId);

    @Modifying(flushAutomatically = true)
    @Query("delete from TokenConfirmacaoEmail t where t.usuario.id = :usuarioId")
    void excluirDoUsuario(@Param("usuarioId") Long usuarioId);

}
