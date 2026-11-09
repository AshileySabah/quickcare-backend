package com.quickcare.backend.seguranca.dto;

import com.quickcare.backend.common.validation.SenhaForte;
import jakarta.validation.constraints.NotBlank;

public record TrocarSenhaRequest(
        @NotBlank(message = "Informe sua senha atual.") String senhaAtual,
        @NotBlank(message = "Crie uma nova senha.") @SenhaForte String novaSenha
) {
}
