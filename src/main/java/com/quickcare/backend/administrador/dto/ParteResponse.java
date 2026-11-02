package com.quickcare.backend.administrador.dto;

import com.quickcare.backend.usuario.Usuario;

/** Uma das pessoas envolvidas, como o admin vê (com e-mail, para contato). */
public record ParteResponse(Long usuarioId, String nome, String email) {

    public static ParteResponse de(Usuario usuario) {
        return new ParteResponse(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }

}
