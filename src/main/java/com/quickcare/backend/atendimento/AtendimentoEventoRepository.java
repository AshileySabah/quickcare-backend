package com.quickcare.backend.atendimento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AtendimentoEventoRepository extends JpaRepository<AtendimentoEvento, Long> {

    List<AtendimentoEvento> findByAtendimentoIdOrderByCriadoEmAscIdAsc(Long atendimentoId);

}
