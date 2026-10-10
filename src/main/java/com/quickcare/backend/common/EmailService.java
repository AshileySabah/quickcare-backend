package com.quickcare.backend.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import jakarta.mail.internet.MimeMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String NOME_REMETENTE = "QuickCare";

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final RestClient brevoClient = RestClient.create("https://api.brevo.com/v3");

    @Value("${quickcare.email.brevo-api-key:}")
    private String brevoApiKey;

    @Value("${spring.mail.username:}")
    private String remetente;

    public void enviarEmailHtml(String destinatario, String assunto, String corpoHtml) {
        executor.submit(() -> {
            try {
                if (brevoApiKey.isBlank()) {
                    enviarPorSmtp(destinatario, assunto, corpoHtml);
                } else {
                    enviarPorBrevo(destinatario, assunto, corpoHtml);
                }
            } catch (Exception e) {
                log.error("Falha ao enviar e-mail \"{}\" para {}", assunto, destinatario, e);
            }
        });
    }

    public void enviarEmailTemplate(String destinatario, String assunto, String nomeTemplate, Map<String, Object> variaveis) {
        Context context = new Context();
        variaveis.forEach(context::setVariable);

        String corpoHtml = templateEngine.process("email/" + nomeTemplate, context);
        enviarEmailHtml(destinatario, assunto, corpoHtml);
    }

    private void enviarPorSmtp(String destinatario, String assunto, String corpoHtml) throws Exception {
        MimeMessage mensagem = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");
        helper.setTo(destinatario);
        helper.setSubject(assunto);
        helper.setText(corpoHtml, true);
        mailSender.send(mensagem);
    }

    private void enviarPorBrevo(String destinatario, String assunto, String corpoHtml) {
        brevoClient.post()
                .uri("/smtp/email")
                .header("api-key", brevoApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sender", Map.of("name", NOME_REMETENTE, "email", remetente),
                        "to", List.of(Map.of("email", destinatario)),
                        "subject", assunto,
                        "htmlContent", corpoHtml
                ))
                .retrieve()
                .toBodilessEntity();
    }
}
