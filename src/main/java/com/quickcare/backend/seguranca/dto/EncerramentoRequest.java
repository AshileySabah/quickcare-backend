package com.quickcare.backend.seguranca.dto;

import com.quickcare.backend.seguranca.AlvoEncerramento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record EncerramentoRequest(
        @NotNull(message = "Escolha o que você quer encerrar.") AlvoEncerramento alvo,
        @NotBlank(message = "Informe o código enviado por e-mail.")
        @Pattern(regexp = "\\d{6}", message = "O código tem 6 números.") String codigo
) {
}
