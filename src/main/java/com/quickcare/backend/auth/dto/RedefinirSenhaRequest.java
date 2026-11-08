package com.quickcare.backend.auth.dto;

import com.quickcare.backend.common.validation.SenhaForte;
import jakarta.validation.constraints.NotBlank;

public record RedefinirSenhaRequest(
        @NotBlank(message = "O link de redefinição é inválido. Peça um novo.") String token,
        @NotBlank(message = "Crie uma senha.") @SenhaForte String novaSenha
) {
}
