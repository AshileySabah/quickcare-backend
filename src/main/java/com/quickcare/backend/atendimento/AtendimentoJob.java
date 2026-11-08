package com.quickcare.backend.atendimento;

import com.quickcare.backend.avaliacao.AvaliacaoService;
import com.quickcare.backend.banimento.BanimentoService;
import com.quickcare.backend.problema.ProblemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AtendimentoJob {

    private final AtendimentoService atendimentoService;
    private final ProblemaService problemaService;
    private final AvaliacaoService avaliacaoService;
    private final BanimentoService banimentoService;

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void processar() {
        atendimentoService.processarVencidos();
        problemaService.processarVencidos();
        avaliacaoService.processarLembretes();
        banimentoService.processarVencidos();
    }

    @EventListener(ApplicationReadyEvent.class)
    void aoSubir() {
        atendimentoService.processarVencidos();
        problemaService.processarVencidos();
        avaliacaoService.processarLembretes();
        banimentoService.processarVencidos();
    }

}
