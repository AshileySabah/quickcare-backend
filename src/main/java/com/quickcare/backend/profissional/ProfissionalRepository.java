package com.quickcare.backend.profissional;

import com.quickcare.backend.validacao.StatusValidacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {

    boolean existsByUsuarioId(Long usuarioId);

    Optional<Profissional> findByUsuarioId(Long usuarioId);

    boolean existsByCnpjAndIdNot(String cnpj, Long id);

    List<Profissional> findByStatusValidacaoOrderByCriadoEmAsc(StatusValidacao statusValidacao);

    List<Profissional> findAllByOrderByCriadoEmAsc();

    @Query("select p from Profissional p where p.usuario.titular.id = :titularId and p.excluidoEm is null")
    Optional<Profissional> findCongeladoDoTitular(@Param("titularId") Long titularId);

    @Query("select p.id from Profissional p where p.encerradoEm <= :limite and p.excluidoEm is null")
    List<Long> findIdsParaExcluir(@Param("limite") Instant limite);

    @Query("""
            select p from Profissional p
            where p.usuario.lapide = true and p.usuario.cpfEncerrado = :cpf and p.excluidoEm is not null
            order by p.encerradoEm desc
            """)
    List<Profissional> findExcluidosDoCpf(@Param("cpf") String cpf);

    @Modifying(flushAutomatically = true)
    @Query(value = "DELETE FROM profissional WHERE usuario_id = :usuarioId", nativeQuery = true)
    int excluirDoUsuario(@Param("usuarioId") Long usuarioId);

}
