package com.quickcare.backend.auth.dto;

import com.quickcare.backend.common.validation.SenhaForte;
import jakarta.validation.constraints.NotBlank;

public record RedefinirSenhaRequest(
        @NotBlank String token,
        @NotBlank @SenhaForte String novaSenha
) {
}
