package com.quickcare.backend.proposta.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Usado tanto no envio quanto na edição da proposta. */
public record PropostaRequest(
        @NotNull @DecimalMin("1.00") @Digits(integer = 8, fraction = 2) BigDecimal valor,
        @NotBlank @Size(min = 20, max = 1000) String abordagem,
        @NotNull @Future Instant inicio,
        @NotNull Instant termino
) {
}
