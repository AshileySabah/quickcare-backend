package com.quickcare.backend.documento;

import com.quickcare.backend.storage.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArmazenamentoDocumentoService {

    private static final Set<String> TIPOS_ACEITOS = Set.of("application/pdf", "image/jpeg", "image/png");
    private static final long TAMANHO_MAXIMO_BYTES = 5L * 1024 * 1024;

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @PostConstruct
    void garantirBucket() throws Exception {
        boolean existe = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.bucket()).build());
        if (!existe) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.bucket()).build());
        }
    }

    /**
     * Envia o arquivo para o bucket e devolve sua storage key. A chave usa um
     * UUID em vez do id da entidade dona porque o upload acontece durante o
     * cadastro, antes do paciente/profissional existir no banco.
     */
    public String enviar(TipoDocumento tipo, MultipartFile arquivo) {
        validar(arquivo);

        String extensao = extrairExtensao(arquivo.getOriginalFilename());
        String key = "%s/%s%s".formatted(tipo.name().toLowerCase(), UUID.randomUUID(), extensao);

        try (var input = arquivo.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.bucket())
                            .object(key)
                            .stream(input, arquivo.getSize(), -1)
                            .contentType(arquivo.getContentType())
                            .build()
            );
        } catch (Exception ex) {
            throw new ArquivoInvalidoException("Não foi possível enviar o arquivo \"%s\". Tente novamente.".formatted(arquivo.getOriginalFilename()));
        }

        return key;
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
