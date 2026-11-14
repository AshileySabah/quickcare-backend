package com.quickcare.backend.chat;

import java.time.Instant;

public record BloqueioNoChat(Long profissionalUsuarioId, Instant desde) {
}
