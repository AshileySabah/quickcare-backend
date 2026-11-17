package com.quickcare.backend.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "quickcare.storage.tipo", havingValue = "supabase", matchIfMissing = true)
public class SupabaseArmazenamento implements ArmazenamentoArquivos {

    private final SupabaseStorageProperties properties;
    private final RestClient.Builder restClientBuilder;

    @Override
    public void enviar(String chave, byte[] conteudo, String contentType) {
        int barra = chave.indexOf('/');
        restClientBuilder.build()
                .post()
                .uri(properties.url() + "/storage/v1/object/{bucket}/{pasta}/{arquivo}",
                        properties.bucket(), chave.substring(0, barra), chave.substring(barra + 1))
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("apikey", properties.apiKey())
                .contentType(MediaType.parseMediaType(contentType))
                .body(conteudo)
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public void excluir(String chave) {
        restClientBuilder.build()
                .delete()
                .uri(properties.url() + "/storage/v1/object/" + properties.bucket() + "/" + chave)
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("apikey", properties.apiKey())
                .retrieve()
                .toBodilessEntity();
    }

    @Override
    public byte[] baixar(String chave) {
        return restClientBuilder.build()
                .get()
                .uri(properties.url() + "/storage/v1/object/authenticated/{bucket}/{chave}", properties.bucket(), chave)
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("apikey", properties.apiKey())
                .retrieve()
                .body(byte[].class);
    }

    @Override
    public String urlAssinada(String chave, Duration expiracao) {
        SignedUrlResponse resposta = restClientBuilder.build()
                .post()
                .uri(properties.url() + "/storage/v1/object/sign/" + properties.bucket() + "/" + chave)
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("apikey", properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new SignedUrlRequest(expiracao.toSeconds()))
                .retrieve()
                .body(SignedUrlResponse.class);
        return resposta == null ? null : properties.url() + "/storage/v1" + resposta.signedURL();
    }

    private record SignedUrlRequest(long expiresIn) {
    }

    private record SignedUrlResponse(String signedURL) {
    }
}
