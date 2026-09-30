package com.quickcare.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TokenRedefinicaoSenhaRepository extends JpaRepository<TokenRedefinicaoSenha, Long> {

    Optional<TokenRedefinicaoSenha> findByTokenHash(String tokenHash);

    Optional<TokenRedefinicaoSenha> findByUsuarioId(Long usuarioId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TokenRedefinicaoSenha t WHERE t.usuario.id = :usuarioId")
    void excluirDoUsuario(Long usuarioId);

}
