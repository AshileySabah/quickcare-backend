package com.quickcare.backend.geolocalizacao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Converte o endereço digitado em latitude/longitude, sem nada a mais para o
 * usuário preencher. Falha de rede ou endereço não encontrado nunca bloqueia o
 * cadastro: devolve vazio e a regra de raio cai no fallback por cidade
 * (ver {@code Solicitacao#atendidaPor}).
 */
@Slf4j
@Service
public class GeocodificacaoService {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final GeocodificacaoProperties properties;
    private final RestClient client;

    public GeocodificacaoService(GeocodificacaoProperties properties, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);

        this.properties = properties;
        this.client = builder
                .baseUrl(properties.url())
                .defaultHeader("User-Agent", properties.userAgent())
                .requestFactory(requestFactory)
                .build();
    }

    /** Tenta primeiro rua + número + cidade; se não achar, só o CEP. */
    public Optional<Coordenadas> geocodificar(EnderecoGeocodificavel endereco) {
        if (!properties.habilitada()) {
            return Optional.empty();
        }

        return buscar(uri -> uri
                .queryParam("street", endereco.numero() + " " + endereco.logradouro())
                .queryParam("city", endereco.cidade())
                .queryParam("state", endereco.uf()))
                .or(() -> buscar(uri -> uri.queryParam("postalcode", formatarCep(endereco.cep()))));
    }

    private Optional<Coordenadas> buscar(Function<UriBuilder, UriBuilder> filtros) {
        try {
            ResultadoNominatim[] resultados = client.get()
                    .uri(uri -> filtros.apply(uri.path("/search")
                                    .queryParam("format", "jsonv2")
                                    .queryParam("limit", 1)
                                    .queryParam("countrycodes", "br"))
                            .build())
                    .retrieve()
                    .body(ResultadoNominatim[].class);

            if (resultados == null || resultados.length == 0) {
                return Optional.empty();
            }

            return Optional.of(new Coordenadas(
                    new BigDecimal(resultados[0].lat()).setScale(6, RoundingMode.HALF_UP),
                    new BigDecimal(resultados[0].lon()).setScale(6, RoundingMode.HALF_UP)));
        } catch (RestClientException | NumberFormatException ex) {
            log.warn("Falha ao geocodificar endereço: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static String formatarCep(String cep) {
        return cep != null && cep.length() == 8 ? cep.substring(0, 5) + "-" + cep.substring(5) : cep;
    }

    private record ResultadoNominatim(String lat, String lon) {
    }

}
