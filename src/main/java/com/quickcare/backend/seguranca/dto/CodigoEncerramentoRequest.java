package com.quickcare.backend.seguranca.dto;

import com.quickcare.backend.seguranca.AlvoEncerramento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CodigoEncerramentoRequest(
        @NotNull(message = "Escolha o que você quer encerrar.") AlvoEncerramento alvo,
        @NotBlank(message = "Informe sua senha atual.") String senha
) {
}
