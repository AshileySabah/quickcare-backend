package com.quickcare.backend.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The MinIO Java client speaks the generic S3 API, so the same client/config
 * works against the local MinIO container in dev and against an S3-compatible
 * bucket (e.g. Supabase Storage) in prod — only these properties change.
 */
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String url,
        String accessKey,
        String secretKey,
        String bucket,
        String region
) {
}
