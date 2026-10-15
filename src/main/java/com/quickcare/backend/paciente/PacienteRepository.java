package com.quickcare.backend.paciente;

import com.quickcare.backend.validacao.StatusValidacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {

    boolean existsByUsuarioId(Long usuarioId);

    Optional<Paciente> findByUsuarioId(Long usuarioId);

    List<Paciente> findByStatusValidacaoOrderByCriadoEmAsc(StatusValidacao statusValidacao);

    List<Paciente> findAllByOrderByCriadoEmAsc();

}
