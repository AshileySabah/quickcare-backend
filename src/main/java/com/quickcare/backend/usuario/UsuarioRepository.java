package com.quickcare.backend.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByCpf(String cpf);

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("select u.id from Usuario u where u.lapide = false and u.encerradoEm <= :limite and u.excluidoEm is null")
    List<Long> findIdsParaExcluir(@Param("limite") Instant limite);

    @Query("select u from Usuario u where u.lapide = false and u.cpfEncerrado = :cpf and u.excluidoEm is not null")
    List<Usuario> findExcluidosDoCpf(@Param("cpf") String cpf);

}
