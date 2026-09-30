package com.quickcare.backend.paciente.dto;

import com.quickcare.backend.cadastro.dto.ContatoEmergenciaRequest;
import com.quickcare.backend.usuario.Genero;

import java.time.LocalDate;
import java.util.List;

public record PerfilPacienteResponse(
        String nome,
        String email,
        String cpf,
        String telefone,
        LocalDate dataNascimento,
        Genero genero,
        List<ContatoEmergenciaRequest> contatosEmergencia,
        String alergias,
        String condicoesSaude,
        String medicamentosEmUso,
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf
) {
}