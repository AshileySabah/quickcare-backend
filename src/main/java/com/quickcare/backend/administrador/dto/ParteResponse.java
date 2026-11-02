package com.quickcare.backend.administrador.dto;

import com.quickcare.backend.usuario.Usuario;

public record ParteResponse(Long usuarioId, String nome, String email) {

    public static ParteResponse de(Usuario usuario) {
        return new ParteResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }

}
