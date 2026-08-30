package com.quickcare.backend.cadastro.dto;

public record CadastroResponse(
        Long usuarioId,
        Long perfilId,
        String nome,
        String email,
        String tipoPerfil
) {
}
