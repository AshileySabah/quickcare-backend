package com.quickcare.backend.auth.dto;

import com.quickcare.backend.usuario.TipoPerfil;
import jakarta.validation.constraints.NotNull;

public record PerfilAtivoRequest(
        @NotNull(message = "Escolha o perfil.") TipoPerfil perfil
) {
}
