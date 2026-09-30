package com.quickcare.backend.perfil.dto;

import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.usuario.Genero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PerfilProfissionalResponse(
        String nome,
        String email,
        String telefone,
        String avatarUrl,
        LocalDate dataNascimento,
        Genero genero,
        List<ContatoEmergenciaResponse> contatosEmergencia,
        boolean possuiSeguroResponsabilidadeCivil,
        ModalidadeAtendimento modalidadeAtendimento,
        BigDecimal raioAtendimentoKm,
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf
) {
}
