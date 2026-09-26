package com.quickcare.backend.documento;

import com.quickcare.backend.storage.SupabaseStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArmazenamentoDocumentoService {

    private static final Set<String> TIPOS_ACEITOS = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long TAMANHO_MAXIMO_BYTES = 5L * 1024 * 1024;

    private final SupabaseStorageProperties properties;
    private final RestClient.Builder restClientBuilder;

    /**
     * Envia o arquivo para o bucket e devolve sua storage key. A chave usa um
     * UUID em vez do id da entidade dona porque o upload acontece durante o
     * cadastro, antes do paciente/profissional existir no banco.
     */
    public String enviar(TipoDocumento tipo, MultipartFile arquivo) {
        validar(arquivo);

        String pasta = tipo.name().toLowerCase();
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

    private void validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ArquivoInvalidoException("Arquivo não informado.");
        }

        if (!TIPOS_ACEITOS.contains(arquivo.getContentType())) {
            throw new ArquivoInvalidoException("Formato inválido para \"%s\". Envie um arquivo PDF, JPG ou PNG.".formatted(arquivo.getOriginalFilename()));
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
