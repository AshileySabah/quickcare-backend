package com.quickcare.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface MensagemNaoLidaRepository extends JpaRepository<MensagemNaoLida, Long> {

    interface ContagemPorCanal {
        Long getSolicitacaoId();

        Long getProfissionalUsuarioId();

        long getQuantidade();
    }

    @Query("""
            select n.solicitacaoId as solicitacaoId, n.profissionalUsuarioId as profissionalUsuarioId,
                   count(n) as quantidade
            from MensagemNaoLida n
            where n.usuarioId = :usuarioId
            group by n.solicitacaoId, n.profissionalUsuarioId
            """)
    List<ContagemPorCanal> contarPorCanal(@Param("usuarioId") Long usuarioId);

    @Query("""
            select count(n) > 0 from MensagemNaoLida n
            where n.usuarioId = :usuarioId and n.solicitacaoId = :solicitacaoId
              and ((:profissionalUsuarioId is null and n.profissionalUsuarioId is null)
                   or n.profissionalUsuarioId = :profissionalUsuarioId)
            """)
    boolean existeNoCanal(
            @Param("usuarioId") Long usuarioId,
            @Param("solicitacaoId") Long solicitacaoId,
            @Param("profissionalUsuarioId") Long profissionalUsuarioId);

    @Modifying
    @Query("""
            delete from MensagemNaoLida n
            where n.usuarioId = :usuarioId and n.solicitacaoId = :solicitacaoId
              and ((:profissionalUsuarioId is null and n.profissionalUsuarioId is null)
                   or n.profissionalUsuarioId = :profissionalUsuarioId)
            """)
    int marcarCanalComoLido(
            @Param("usuarioId") Long usuarioId,
            @Param("solicitacaoId") Long solicitacaoId,
            @Param("profissionalUsuarioId") Long profissionalUsuarioId);

    @Modifying
    @Query("delete from MensagemNaoLida n where n.mensagemId = :mensagemId")
    int excluirDaMensagem(@Param("mensagemId") Long mensagemId);

    @Query("""
            select distinct n.usuarioId from MensagemNaoLida n
            where n.emailEnviadoEm is null and n.criadaEm <= :limite
            """)
    List<Long> usuariosComAvisoPendente(@Param("limite") Instant limite);

    List<MensagemNaoLida> findByUsuarioIdAndEmailEnviadoEmIsNull(Long usuarioId);

}
