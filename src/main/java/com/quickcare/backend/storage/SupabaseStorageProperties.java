package com.quickcare.backend.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * O projeto tem apenas as chaves de API gerais do Supabase (publishable/secret),
 * não um par de chaves de acesso S3 — por isso o upload usa a API REST própria
 * do Supabase Storage (/storage/v1/object/...) em vez do endpoint S3-compatível.
 */
@ConfigurationProperties(prefix = "supabase.storage")
public record SupabaseStorageProperties(
        String url,
        String apiKey,
        String bucket
) {
}
