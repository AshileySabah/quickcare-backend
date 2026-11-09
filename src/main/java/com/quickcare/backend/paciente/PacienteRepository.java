package com.quickcare.backend.paciente;

import com.quickcare.backend.validacao.StatusValidacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    boolean existsByUsuarioId(Long usuarioId);

    Optional<Paciente> findByUsuarioId(Long usuarioId);

    List<Paciente> findByStatusValidacaoOrderByCriadoEmAsc(StatusValidacao statusValidacao);

    List<Paciente> findAllByOrderByCriadoEmAsc();

    @Query("select p from Paciente p where p.usuario.titular.id = :titularId and p.excluidoEm is null")
    Optional<Paciente> findCongeladoDoTitular(@Param("titularId") Long titularId);

    @Query("select p.id from Paciente p where p.encerradoEm <= :limite and p.excluidoEm is null")
    List<Long> findIdsParaExcluir(@Param("limite") Instant limite);

    @Query("""
            select p from Paciente p
            where p.usuario.lapide = true and p.usuario.cpfEncerrado = :cpf and p.excluidoEm is not null
            order by p.encerradoEm desc
            """)
    List<Paciente> findExcluidosDoCpf(@Param("cpf") String cpf);

}
