package com.quickcare.backend.auth.dto;

import com.quickcare.backend.validacao.StatusValidacao;

/**
 * `status` diz se o perfil está ativo ou banido; `statusValidacao` é a situação
 * da validação cadastral (nula para o admin, que não passa por ela).
 */
public record PerfilStatusResponse(
        String tipo,
        String status,
        StatusValidacao statusValidacao
) {
}
