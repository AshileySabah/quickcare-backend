package com.quickcare.backend.documento;

import com.quickcare.backend.storage.SupabaseStorageProperties;
import com.quickcare.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArmazenamentoDocumentoService {

    private static final Set<String> TIPOS_ACEITOS = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final Set<String> TIPOS_IMAGEM_ACEITOS = Set.of("image/jpeg", "image/png");
    private static final Set<String> TIPOS_ANEXO_CHAT_ACEITOS = Set.of(
            "image/jpeg", "image/png", "application/pdf", "video/mp4",
            "audio/mpeg", "audio/mp4", "audio/x-m4a", "audio/ogg", "audio/webm", "audio/wav", "audio/x-wav");
    private static final long TAMANHO_MAXIMO_MB = 5;
    private static final long TAMANHO_MAXIMO_ANEXO_CHAT_MB = 15;
    private static final Duration EXPIRACAO_URL_ASSINADA = Duration.ofHours(1);
    private static final Duration EXPIRACAO_URL_ASSINADA_CHAT = Duration.ofHours(24);

    private final SupabaseStorageProperties properties;
    private final RestClient.Builder restClientBuilder;

    public String enviar(TipoDocumento tipo, MultipartFile arquivo) {
        validar(arquivo, TIPOS_ACEITOS, "Envie um arquivo PDF, JPG ou PNG.");
        return enviarParaPasta(tipo.name().toLowerCase(), arquivo);
    }

    public String enviarAvatar(MultipartFile arquivo) {
        validar(arquivo, TIPOS_IMAGEM_ACEITOS, "Envie uma imagem JPG ou PNG.");
        return enviarParaPasta("avatares", arquivo);
    }

    public String enviarAnexoValidacao(MultipartFile arquivo) {
        validar(arquivo, TIPOS_ACEITOS, "Envie um arquivo PDF, JPG ou PNG.");
        return enviarParaPasta("anexos-validacao", arquivo);
    }

    public String enviarAnexoProblema(MultipartFile arquivo) {
        validar(arquivo, TIPOS_ACEITOS, "Envie um arquivo PDF, JPG ou PNG.");
        return enviarParaPasta("anexos-problema", arquivo);
    }

    /** Anexos do chat (imagem, PDF, vídeo ou áudio) ficam em pasta própria, com limite maior. */
    public String enviarAnexoChat(MultipartFile arquivo) {
        validar(arquivo, TIPOS_ANEXO_CHAT_ACEITOS,
                "Envie uma imagem JPG/PNG, um PDF, um vídeo MP4 ou um áudio (MP3, M4A, OGG, WAV).",
                TAMANHO_MAXIMO_ANEXO_CHAT_MB);
        return enviarParaPasta("chat-anexos", arquivo);
    }

    public String enviarAnexoDenuncia(MultipartFile arquivo) {
        validar(arquivo, TIPOS_ACEITOS, "Envie um arquivo PDF, JPG ou PNG.");
        return enviarParaPasta("anexos-denuncia", arquivo);
    }

    public void validarAnexo(MultipartFile arquivo) {
        validar(arquivo, TIPOS_ACEITOS, "Envie um arquivo PDF, JPG ou PNG.");
    }

    private String enviarParaPasta(String pasta, MultipartFile arquivo) {
        String nomeArquivo = UUID.randomUUID() + extrairExtensao(arquivo.getOriginalFilename());

        try {
            restClientBuilder.build()
                    .post()
                    .uri(properties.url() + "/storage/v1/object/{bucket}/{pasta}/{arquivo}", properties.bucket(), pasta, nomeArquivo)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("apikey", properties.apiKey())
                    .contentType(MediaType.parseMediaType(arquivo.getContentType()))
                    .body(arquivo.getBytes())
                    .retrieve()
                    .toBodilessEntity();
        } catch (IOException ex) {
            throw new ArquivoInvalidoException("Não foi possível ler o arquivo \"%s\".".formatted(arquivo.getOriginalFilename()));
        } catch (Exception ex) {
            log.warn("Falha ao enviar \"{}\" para o storage em {}: {}", arquivo.getOriginalFilename(), properties.url(), ex.getMessage());
            throw new ArquivoInvalidoException("Não foi possível enviar o arquivo \"%s\". Tente novamente.".formatted(arquivo.getOriginalFilename()));
        }

        return pasta + "/" + nomeArquivo;
    }

    public void excluir(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            restClientBuilder.build()
                    .delete()
                    .uri(properties.url() + "/storage/v1/object/" + properties.bucket() + "/" + storageKey)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("apikey", properties.apiKey())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Falha ao excluir \"{}\" do storage: {}", storageKey, ex.getMessage());
        }
    }

    public byte[] baixar(String storageKey) {
        try {
            byte[] conteudo = restClientBuilder.build()
                    .get()
                    .uri(properties.url() + "/storage/v1/object/authenticated/{bucket}/{chave}", properties.bucket(), storageKey)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("apikey", properties.apiKey())
                    .retrieve()
                    .body(byte[].class);
            if (conteudo == null) {
                throw new ArquivoInvalidoException("Arquivo indisponível.");
            }
            return conteudo;
        } catch (ArquivoInvalidoException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Falha ao baixar \"{}\" do storage: {}", storageKey, ex.getMessage());
            throw new ArquivoInvalidoException("Não foi possível abrir o arquivo. Tente novamente.");
        }
    }

    public String urlDoAvatar(Usuario usuario) {
        return usuario.getAvatarStorageKey() != null ? gerarUrlAssinada(usuario.getAvatarStorageKey()) : null;
    }

    public String gerarUrlAssinada(String storageKey) {
        return gerarUrlAssinada(storageKey, EXPIRACAO_URL_ASSINADA);
    }

    /** Os anexos do chat valem por 24h para a conversa não quebrar enquanto a tela fica aberta. */
    public String urlDoAnexoChat(String storageKey) {
        return gerarUrlAssinada(storageKey, EXPIRACAO_URL_ASSINADA_CHAT);
    }

    private String gerarUrlAssinada(String storageKey, Duration expiracao) {
        try {
            // A chave tem barras ("pasta/arquivo"): concatenar, e não usar variavel de template,
            // senao o Spring codifica a barra como %2F e o Storage responde InvalidSignature.
            SignedUrlResponse resposta = restClientBuilder.build()
                    .post()
                    .uri(properties.url() + "/storage/v1/object/sign/" + properties.bucket() + "/" + storageKey)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("apikey", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SignedUrlRequest(expiracao.toSeconds()))
                    .retrieve()
                    .body(SignedUrlResponse.class);

            return resposta == null ? null : properties.url() + "/storage/v1" + resposta.signedURL();
        } catch (Exception ex) {
            return null;
        }
    }

    private record SignedUrlRequest(long expiresIn) {
    }

    private record SignedUrlResponse(String signedURL) {
    }

    private void validar(MultipartFile arquivo, Set<String> tiposAceitos, String mensagemFormatoInvalido) {
        validar(arquivo, tiposAceitos, mensagemFormatoInvalido, TAMANHO_MAXIMO_MB);
    }

    private void validar(MultipartFile arquivo, Set<String> tiposAceitos, String mensagemFormatoInvalido, long tamanhoMaximoMb) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ArquivoInvalidoException("Arquivo não informado.");
        }

        if (!tiposAceitos.contains(arquivo.getContentType())) {
            throw new ArquivoInvalidoException("Formato inválido para \"%s\". %s".formatted(arquivo.getOriginalFilename(), mensagemFormatoInvalido));
        }

        if (arquivo.getSize() > tamanhoMaximoMb * 1024 * 1024) {
            throw new ArquivoInvalidoException("O arquivo \"%s\" deve ter no máximo %dMB.".formatted(arquivo.getOriginalFilename(), tamanhoMaximoMb));
        }
    }

    private String extrairExtensao(String nomeOriginal) {
        if (nomeOriginal == null) {
            return "";
        }

        int ponto = nomeOriginal.lastIndexOf('.');
        return ponto >= 0 ? nomeOriginal.substring(ponto) : "";
    }

}
