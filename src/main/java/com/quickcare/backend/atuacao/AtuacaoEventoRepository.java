package com.quickcare.backend.atuacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AtuacaoEventoRepository extends JpaRepository<AtuacaoEvento, Long> {

    List<AtuacaoEvento> findByAtuacaoIdOrderByCriadoEmAscIdAsc(Long atuacaoId);

}
