package com.quickcare.backend.auth.dto;

import com.quickcare.backend.usuario.TipoPerfil;

import java.util.List;

public record LoginResponse(
        Long usuarioId,
        String nome,
        String email,
        String avatarUrl,
        List<PerfilStatusResponse> perfis,
        TipoPerfil ultimoPerfil
) {
}
