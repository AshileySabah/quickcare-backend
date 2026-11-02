package com.quickcare.backend.perfil.dto;

import com.quickcare.backend.usuario.ContatoEmergencia;

import java.util.List;

public record ContatoEmergenciaResponse(
        String nome,
        String telefone
) {

    public static List<ContatoEmergenciaResponse> de(List<ContatoEmergencia> contatos) {
        return contatos.stream()
                .map(contato -> new ContatoEmergenciaResponse(contato.getNome(), contato.getTelefone()))
                .toList();
    }

}
