package com.quickcare.backend.common;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
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
                // idealmente logar com um logger de verdade (SLF4J), não deixar engolir silenciosamente
                System.err.println("Erro ao enviar e-mail: " + e.getMessage());
            }
        });
    }
}
