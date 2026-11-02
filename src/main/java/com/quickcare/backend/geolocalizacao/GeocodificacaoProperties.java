package com.quickcare.backend.geolocalizacao;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "quickcare.geocodificacao")
public record GeocodificacaoProperties(
        boolean habilitada,
        String url,
        String userAgent
) {
}
