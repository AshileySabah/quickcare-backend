package com.quickcare.backend;

import com.quickcare.backend.jwt.JwtProperties;
import com.quickcare.backend.storage.MinioProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, MinioProperties.class})
public class QuickcareBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuickcareBackendApplication.class, args);
    }

}
