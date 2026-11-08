package com.quickcare.backend.atuacao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AtuacaoRepository extends JpaRepository<Atuacao, Long> {

    List<Atuacao> findByProfissionalIdAndRemovidoEmIsNullOrderByCriadoEmAscIdAsc(Long profissionalId);

    Optional<Atuacao> findByIdAndProfissionalId(Long id, Long profissionalId);

}
