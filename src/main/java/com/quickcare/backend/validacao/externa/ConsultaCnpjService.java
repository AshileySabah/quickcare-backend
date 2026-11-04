package com.quickcare.backend.validacao.externa;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ConsultaCnpjService {

    static final String PROVEDOR = "BrasilAPI (Receita Federal)";
    private static final String CAMPO = "CNPJ";
    private static final String SITUACAO_ATIVA = "ATIVA";

    private final RestClient client;

    public ConsultaCnpjService(ValidacaoExternaProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(properties.timeoutSegundos()));
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.timeoutSegundos()));

        this.client = builder
                .baseUrl(properties.cnpj().url())
                .requestFactory(requestFactory)
                .build();
    }

    public VerificacaoExterna verificar(String cnpj, String nomeProfissional) {
        RespostaBrasilApi resposta;
        try {
            resposta = client.get()
                    .uri("/cnpj/v1/{cnpj}", cnpj)
                    .retrieve()
                    .body(RespostaBrasilApi.class);
        } catch (HttpClientErrorException ex) {
            return tratarErro(ex);
        } catch (RestClientException ex) {
            log.warn("Falha ao consultar CNPJ na BrasilAPI: {}", ex.getMessage());
            return VerificacaoExterna.indisponivel(CAMPO, PROVEDOR, "Não foi possível consultar a BrasilAPI agora.");
        }

        if (resposta == null) {
            return VerificacaoExterna.indisponivel(CAMPO, PROVEDOR, "A BrasilAPI respondeu sem dados.");
        }

        return avaliar(resposta, nomeProfissional);
    }

    private VerificacaoExterna tratarErro(HttpClientErrorException ex) {
        if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
            return new VerificacaoExterna(CAMPO, PROVEDOR, Parecer.DESFAVORAVEL,
                    List.of("CNPJ não encontrado na base da Receita."), List.of());
        }
        if (ex.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
            return new VerificacaoExterna(CAMPO, PROVEDOR, Parecer.DESFAVORAVEL,
                    List.of("A BrasilAPI recusou o número como CNPJ inválido."), List.of());
        }
        if (ex.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            return VerificacaoExterna.indisponivel(CAMPO, PROVEDOR, "Limite de consultas da BrasilAPI excedido.");
        }
        log.warn("BrasilAPI respondeu {} ao consultar CNPJ", ex.getStatusCode());
        return VerificacaoExterna.indisponivel(CAMPO, PROVEDOR, "A BrasilAPI respondeu com erro (" + ex.getStatusCode().value() + ").");
    }

    static VerificacaoExterna avaliar(RespostaBrasilApi resposta, String nomeProfissional) {
        List<String> observacoes = new ArrayList<>();
        observacoes.add("Razão social: " + resposta.razaoSocial() + ".");
        if (resposta.nomeFantasia() != null && !resposta.nomeFantasia().isBlank()) {
            observacoes.add("Nome fantasia: " + resposta.nomeFantasia() + ".");
        }

        String nomeNormalizado = TextoComparavel.normalizar(nomeProfissional);
        boolean ehSocio = resposta.qsa() != null && resposta.qsa().stream()
                .anyMatch(socio -> TextoComparavel.normalizar(socio.nomeSocio()).equals(nomeNormalizado));
        observacoes.add(ehSocio
                ? nomeProfissional + " consta no quadro de sócios."
                : nomeProfissional + " não consta no quadro de sócios (pode atuar pela empresa sem ser sócio).");

        String situacao = resposta.situacao() == null ? "desconhecida" : resposta.situacao();
        if (SITUACAO_ATIVA.equalsIgnoreCase(situacao)) {
            return new VerificacaoExterna(CAMPO, PROVEDOR, Parecer.FAVORAVEL,
                    List.of("CNPJ encontrado na base da Receita.", "Situação cadastral: " + situacao + "."),
                    observacoes);
        }

        return new VerificacaoExterna(CAMPO, PROVEDOR, Parecer.DESFAVORAVEL,
                List.of("Situação cadastral não está ativa: " + situacao + "."), observacoes);
    }

    record RespostaBrasilApi(
            @JsonProperty("razao_social") String razaoSocial,
            @JsonProperty("nome_fantasia") String nomeFantasia,
            @JsonProperty("descricao_situacao_cadastral") String situacao,
            List<Socio> qsa
    ) {
    }

    record Socio(@JsonProperty("nome_socio") String nomeSocio) {
    }

}
