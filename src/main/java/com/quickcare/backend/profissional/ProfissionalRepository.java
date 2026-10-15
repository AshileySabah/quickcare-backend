package com.quickcare.backend.profissional;

import com.quickcare.backend.validacao.StatusValidacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfissionalRepository extends JpaRepository<Profissional, Long> {

    boolean existsByUsuarioId(Long usuarioId);

    Optional<Profissional> findByUsuarioId(Long usuarioId);

    boolean existsByCnpjAndIdNot(String cnpj, Long id);

    List<Profissional> findByStatusValidacaoOrderByCriadoEmAsc(StatusValidacao statusValidacao);

    List<Profissional> findAllByOrderByCriadoEmAsc();

}
