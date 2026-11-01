package com.quickcare.backend.keepalive;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "quickcare.keep-alive", name = "habilitado", havingValue = "true")
public class KeepAliveJob {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    static final String HEALTH_PATH = "/actuator/health";

    private final RestClient client;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public KeepAliveJob(KeepAliveProperties properties, RestClient.Builder builder, JdbcTemplate jdbcTemplate) {
        this(properties.url(), builder.requestFactory(comTimeout()), jdbcTemplate);
    }

    /** Nos testes o builder já chega com o request factory do MockRestServiceServer. */
    KeepAliveJob(String url, RestClient.Builder builderConfigurado, JdbcTemplate jdbcTemplate) {
        boolean configurado = url != null && !url.isBlank();
        this.client = configurado ? builderConfigurado.baseUrl(url).build() : null;
        this.jdbcTemplate = jdbcTemplate;

        if (!configurado) {
            log.warn("Keep-alive habilitado sem URL pública (RENDER_EXTERNAL_URL); o ping não será feito.");
        }
    }

    /**
     * O Render hiberna após 15 min sem tráfego externo: precisa ser a URL
     * pública, que passa pelo proxy dele. fixedRate para o intervalo não
     * crescer com a duração da requisição.
     */
    @Scheduled(initialDelayString = "PT1M", fixedRateString = "PT14M")
    public void pingarServico() {
        if (client == null) {
            return;
        }

        try {
            client.get().uri(HEALTH_PATH).retrieve().toBodilessEntity();
            log.debug("Keep-alive: ping ok.");
        } catch (RestClientException ex) {
            log.warn("Keep-alive: falha no ping: {}", ex.getMessage());
        }
    }

    /** O Supabase pausa o projeto após 7 dias sem uso. */
    @Scheduled(cron = "0 0 3 * * *", zone = "America/Sao_Paulo")
    public void consultarBanco() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            log.debug("Keep-alive: banco ok.");
        } catch (DataAccessException ex) {
            log.warn("Keep-alive: falha na consulta ao banco: {}", ex.getMessage());
        }
    }

    private static SimpleClientHttpRequestFactory comTimeout() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);
        return requestFactory;
    }
}
