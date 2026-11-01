package com.quickcare.backend;

import com.quickcare.backend.administrador.AdministradorInicialProperties;
import com.quickcare.backend.geolocalizacao.GeocodificacaoProperties;
import com.quickcare.backend.jwt.JwtProperties;
import com.quickcare.backend.keepalive.KeepAliveProperties;
import com.quickcare.backend.storage.SupabaseStorageProperties;
import com.quickcare.backend.validacao.externa.ValidacaoExternaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, SupabaseStorageProperties.class, GeocodificacaoProperties.class,
        ValidacaoExternaProperties.class, AdministradorInicialProperties.class, KeepAliveProperties.class})
public class QuickcareBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickcareBackendApplication.class, args);
    }

}
