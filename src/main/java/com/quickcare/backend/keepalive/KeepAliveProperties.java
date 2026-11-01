package com.quickcare.backend.keepalive;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "quickcare.keep-alive")
public record KeepAliveProperties(
        boolean habilitado,
        String url
        ) {

}
