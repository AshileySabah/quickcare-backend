package com.quickcare.backend.geolocalizacao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Serviço de geocodificação compatível com a API de busca do Nominatim
 * (OpenStreetMap). A política de uso do servidor público exige um User-Agent
 * que identifique a aplicação e no máximo 1 requisição por segundo.
 */
@ConfigurationProperties(prefix = "quickcare.geocodificacao")
public record GeocodificacaoProperties(
        boolean habilitada,
        String url,
        String userAgent
) {
}
