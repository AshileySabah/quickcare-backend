package com.quickcare.backend.seguranca.dto;

import jakarta.validation.constraints.NotBlank;

public record DesfazerEmailRequest(
        @NotBlank(message = "O link para desfazer a troca é inválido.") String token,
        @NotBlank(message = "Informe a senha que a conta tinha antes da troca.") String senha
) {
}
