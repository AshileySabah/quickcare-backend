package com.quickcare.backend.storage;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(minioProperties.url())
                .credentials(minioProperties.accessKey(), minioProperties.secretKey());

        if (minioProperties.region() != null && !minioProperties.region().isBlank()) {
            builder.region(minioProperties.region());
        }

        return builder.build();
    }

}
