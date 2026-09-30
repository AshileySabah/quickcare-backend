package com.quickcare.backend.common;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import jakarta.mail.internet.MimeMessage;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void enviarEmailHtml(String destinatario, String assunto, String corpoHtml) {
        executor.submit(() -> {
            try {
                MimeMessage mensagem = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");
                helper.setTo(destinatario);
                helper.setSubject(assunto);
                helper.setText(corpoHtml, true);
                mailSender.send(mensagem);
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
}
