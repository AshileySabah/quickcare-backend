package com.quickcare.backend.validacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EtapaValidacaoRepository extends JpaRepository<EtapaValidacao, Long> {

    List<EtapaValidacao> findByPacienteIdOrderByCriadoEmAscIdAsc(Long pacienteId);

    List<EtapaValidacao> findByProfissionalIdOrderByCriadoEmAscIdAsc(Long profissionalId);

}
