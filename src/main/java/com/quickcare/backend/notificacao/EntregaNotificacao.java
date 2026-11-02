package com.quickcare.backend.notificacao;

import com.quickcare.backend.common.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * Entrega a notificação já gravada: pelo WebSocket (se o usuário estiver
 * conectado) e, quando pedido, por e-mail. Falha na entrega não desfaz nada:
 * a notificação continua no banco e aparece na próxima carga do sino.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class EntregaNotificacao {

    static final String DESTINO = "/queue/notificacoes";

    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;

    @Value("${quickcare.frontend-url}")
    private String frontendUrl;

    @TransactionalEventListener(fallbackExecution = true)
    void entregar(NotificacaoCriada evento) {
        try {
            messagingTemplate.convertAndSendToUser(evento.usuarioId().toString(), DESTINO, evento.notificacao());
        } catch (RuntimeException ex) {
            log.warn("Não foi possível entregar a notificação {} em tempo real.", evento.notificacao().id(), ex);
        }

        if (evento.email() != null) {
            enviarEmail(evento);
        }
    }

    private void enviarEmail(NotificacaoCriada evento) {
        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("nome", evento.email().nome());
        variaveis.put("titulo", evento.notificacao().titulo());
        variaveis.put("mensagem", evento.notificacao().mensagem());
        variaveis.put("link", evento.notificacao().link() == null
                ? frontendUrl
                : UriComponentsBuilder.fromUriString(frontendUrl).path(evento.notificacao().link()).toUriString());

        emailService.enviarEmailTemplate(
                evento.email().destinatario(),
                evento.notificacao().titulo() + " - QuickCare",
                "notificacao",
                variaveis);
    }

}
