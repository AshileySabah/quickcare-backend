package com.quickcare.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    List<Mensagem> findBySolicitacaoIdAndAtivoTrueOrderByDataEnvioAsc(Long solicitacaoId);

}
