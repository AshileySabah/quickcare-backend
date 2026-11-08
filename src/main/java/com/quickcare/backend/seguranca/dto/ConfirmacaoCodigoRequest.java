package com.quickcare.backend.seguranca.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmacaoCodigoRequest(
        @NotBlank(message = "Informe o código enviado por e-mail.")
        @Pattern(regexp = "\\d{6}", message = "O código tem 6 números.") String codigo
) {
}
