package com.quickcare.backend.atendimento.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Novo período proposto. Em andamento, o início tem de ser o atual (só o término muda). */
public record RemarcacaoRequest(@NotNull Instant inicio, @NotNull Instant termino) {
}
