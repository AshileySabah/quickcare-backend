package com.quickcare.backend.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalArmazenamentoTest {

    private static final Instant AGORA = Instant.parse("2026-11-16T12:00:00Z");

    @TempDir
    Path pasta;

    @Test
    void guardaBaixaEExcluiOArquivoNaPastaConfigurada() {
        LocalArmazenamento armazenamento = criar(AGORA);
        byte[] conteudo = "documento".getBytes(StandardCharsets.UTF_8);

        armazenamento.enviar("avatares/foto.png", conteudo, "image/png");

        assertThat(pasta.resolve("avatares/foto.png")).exists();
        assertThat(armazenamento.baixar("avatares/foto.png")).isEqualTo(conteudo);

        armazenamento.excluir("avatares/foto.png");

        assertThat(Files.exists(pasta.resolve("avatares/foto.png"))).isFalse();
    }

    @Test
    void urlAssinadaApontaParaAApiEValeAteExpirar() {
        LocalArmazenamento armazenamento = criar(AGORA);

        String url = armazenamento.urlAssinada("chat-anexos/audio.mp3", Duration.ofHours(24));

        assertThat(url).startsWith("http://localhost:8080/arquivos-locais/chat-anexos/audio.mp3?");
        Map<String, String> parametros = parametros(url);
        long expira = Long.parseLong(parametros.get("expira"));
        assertThat(expira).isEqualTo(AGORA.plus(Duration.ofHours(24)).getEpochSecond());
        assertThat(armazenamento.assinaturaValida("chat-anexos/audio.mp3", expira, parametros.get("assinatura"))).isTrue();
        assertThat(armazenamento.assinaturaValida("chat-anexos/outro.mp3", expira, parametros.get("assinatura"))).isFalse();
        assertThat(armazenamento.assinaturaValida("chat-anexos/audio.mp3", expira + 60, parametros.get("assinatura"))).isFalse();

    }

    @Test
    void urlAssinadaVencidaNaoValeMais() {
        LocalArmazenamento armazenamento = criar(AGORA);

        Map<String, String> parametros = parametros(armazenamento.urlAssinada("avatares/foto.png", Duration.ofMinutes(-1)));

        assertThat(armazenamento.assinaturaValida("avatares/foto.png",
                Long.parseLong(parametros.get("expira")), parametros.get("assinatura"))).isFalse();
    }

    @Test
    void naoAceitaChaveQueSaiDaPasta() {
        LocalArmazenamento armazenamento = criar(AGORA);

        assertThatThrownBy(() -> armazenamento.enviar("../fora.txt", new byte[]{1}, "text/plain"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> armazenamento.baixar("avatares/../../fora.txt"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private LocalArmazenamento criar(Instant agora) {
        return new LocalArmazenamento(pasta.toString(), "http://localhost:8080/", Clock.fixed(agora, ZoneOffset.UTC));
    }

    private static Map<String, String> parametros(String url) {
        Map<String, String> parametros = new HashMap<>();
        for (String par : URI.create(url).getQuery().split("&")) {
            String[] partes = par.split("=", 2);
            parametros.put(partes[0], partes[1]);
        }
        return parametros;
    }
}
