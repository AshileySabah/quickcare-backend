package com.quickcare.backend.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ChatJob {

    private final MensagemNaoLidaService mensagemNaoLidaService;

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void avisarMensagensNaoLidas() {
        mensagemNaoLidaService.avisarPorEmail();
    }

}
