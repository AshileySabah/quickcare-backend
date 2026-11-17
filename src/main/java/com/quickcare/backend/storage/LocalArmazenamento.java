package com.quickcare.backend.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

@Component
@ConditionalOnProperty(name = "quickcare.storage.tipo", havingValue = "local")
public class LocalArmazenamento implements ArmazenamentoArquivos {

    static final String ROTA = "/arquivos-locais/";

    private final Path raiz;
    private final String urlBase;
    private final Clock clock;
    private final byte[] segredo = new byte[32];

    public LocalArmazenamento(
            @Value("${quickcare.storage.local.diretorio:arquivos-locais}") String diretorio,
            @Value("${quickcare.storage.local.url-base:http://localhost:8080}") String urlBase,
            Clock clock
    ) {
        this.raiz = Path.of(diretorio).toAbsolutePath().normalize();
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.clock = clock;
        new SecureRandom().nextBytes(segredo);
    }

    @Override
    public void enviar(String chave, byte[] conteudo, String contentType) {
        Path destino = caminho(chave);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, conteudo);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void excluir(String chave) {
        try {
            Files.deleteIfExists(caminho(chave));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public byte[] baixar(String chave) {
        try {
            return Files.readAllBytes(caminho(chave));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public String urlAssinada(String chave, Duration expiracao) {
        long expira = clock.instant().plus(expiracao).getEpochSecond();
        return urlBase + ROTA + chave + "?expira=" + expira + "&assinatura=" + assinar(chave, expira);
    }

    public boolean assinaturaValida(String chave, long expira, String assinatura) {
        if (assinatura == null || clock.instant().getEpochSecond() > expira) {
            return false;
        }
        return MessageDigest.isEqual(
                assinar(chave, expira).getBytes(StandardCharsets.US_ASCII),
                assinatura.getBytes(StandardCharsets.US_ASCII));
    }

    Path caminho(String chave) {
        Path caminho = raiz.resolve(chave).normalize();
        if (!caminho.startsWith(raiz) || caminho.equals(raiz)) {
            throw new IllegalArgumentException("Chave de arquivo inválida.");
        }
        return caminho;
    }

    private String assinar(String chave, long expira) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
            byte[] assinatura = mac.doFinal((chave + ":" + expira).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(assinatura);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
