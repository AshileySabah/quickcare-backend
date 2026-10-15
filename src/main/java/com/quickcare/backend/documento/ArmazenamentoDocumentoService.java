package com.quickcare.backend.documento;

import com.quickcare.backend.storage.SupabaseStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArmazenamentoDocumentoService {

    private static final Set<String> TIPOS_ACEITOS = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final Set<String> TIPOS_IMAGEM_ACEITOS = Set.of("image/jpeg", "image/png");
    private static final long TAMANHO_MAXIMO_BYTES = 5L * 1024 * 1024;
    private static final Duration EXPIRACAO_URL_ASSINADA = Duration.ofHours(1);

    private final SupabaseStorageProperties properties;
    private final RestClient.Builder restClientBuilder;

    /**
     * Envia o arquivo para o bucket e devolve sua storage key. A chave usa um
     * UUID em vez do id da entidade dona porque o upload acontece durante o
     * cadastro, antes do paciente/profissional existir no banco.
     */
    public String enviar(TipoDocumento tipo, MultipartFile arquivo) {
        validar(arquivo, TIPOS_ACEITOS, "Envie um arquivo PDF, JPG ou PNG.");
        return enviarParaPasta(tipo.name().toLowerCase(), arquivo);
    }

    /**
     * Fotos de perfil não têm um TipoDocumento associado (não são documentos de
     * validação cadastral), então vivem em sua própria pasta dentro do mesmo bucket.
     */
    public String enviarAvatar(MultipartFile arquivo) {
        validar(arquivo, TIPOS_IMAGEM_ACEITOS, "Envie uma imagem JPG ou PNG.");
        return enviarParaPasta("avatares", arquivo);
    }

    /** Evidência anexada pelo admin ao aprovar/reprovar uma etapa da validação cadastral. */
    public String enviarAnexoValidacao(MultipartFile arquivo) {
        validar(arquivo, TIPOS_ACEITOS, "Envie um arquivo PDF, JPG ou PNG.");
        return enviarParaPasta("anexos-validacao", arquivo);
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
            throw new ArquivoInvalidoException("Não foi possível enviar o arquivo \"%s\". Tente novamente.".formatted(arquivo.getOriginalFilename()));
        }

        return pasta + "/" + nomeArquivo;
    }

    /**
     * Gera uma URL assinada e temporária para leitura de um objeto privado, já que
     * o bucket de documentos/avatares não é público.
     */
    public String gerarUrlAssinada(String storageKey) {
        try {
            SignedUrlResponse resposta = restClientBuilder.build()
                    .post()
                    .uri(properties.url() + "/storage/v1/object/sign/{bucket}/{chave}", properties.bucket(), storageKey)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("apikey", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SignedUrlRequest(EXPIRACAO_URL_ASSINADA.toSeconds()))
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
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ArquivoInvalidoException("Arquivo não informado.");
        }

        if (!tiposAceitos.contains(arquivo.getContentType())) {
            throw new ArquivoInvalidoException("Formato inválido para \"%s\". %s".formatted(arquivo.getOriginalFilename(), mensagemFormatoInvalido));
        }

        if (arquivo.getSize() > TAMANHO_MAXIMO_BYTES) {
            throw new ArquivoInvalidoException("O arquivo \"%s\" deve ter no máximo 5MB.".formatted(arquivo.getOriginalFilename()));
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
