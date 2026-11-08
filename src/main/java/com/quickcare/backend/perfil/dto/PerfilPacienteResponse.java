package com.quickcare.backend.perfil.dto;

import com.quickcare.backend.usuario.Genero;

import java.time.LocalDate;
import java.util.List;

public record PerfilPacienteResponse(
        String nome,
        String email,
        String telefone,
        String avatarUrl,
        LocalDate dataNascimento,
        Genero genero,
        List<ContatoEmergenciaResponse> contatosEmergencia,
        String alergias,
        String condicoesSaude,
        String medicamentosEmUso,
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf,
        DeclaracoesResponse declaracoes
) {
}
