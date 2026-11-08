package com.quickcare.backend.proposta.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public record PropostaRequest(
        @NotNull(message = "Informe o valor da proposta.")
        @DecimalMin(value = "1.00", message = "O valor deve ser de pelo menos R$ 1,00.")
        @Digits(integer = 8, fraction = 2, message = "O valor pode ter até 8 dígitos antes da vírgula e 2 depois.") BigDecimal valor,
        @NotBlank(message = "Descreva como você vai realizar o atendimento.")
        @Size(min = 20, max = 1000, message = "A descrição deve ter entre 20 e 1000 caracteres.") String abordagem,
        @NotNull(message = "Informe o início do atendimento.")
        @Future(message = "O início do atendimento precisa ser no futuro.") Instant inicio,
        @NotNull(message = "Informe o término do atendimento.") Instant termino,
        Long atuacaoId
) {
}
