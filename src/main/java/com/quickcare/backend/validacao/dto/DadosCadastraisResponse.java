package com.quickcare.backend.validacao.dto;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.usuario.Genero;
import com.quickcare.backend.usuario.TipoDocumentoIdentidade;

import java.time.LocalDate;
import java.util.List;

/** Dados conferidos nas etapas. Campos de profissional ficam nulos para paciente. */
public record DadosCadastraisResponse(
        String nome,
        String email,
        String telefone,
        String avatarUrl,
        String cpf,
        LocalDate dataNascimento,
        Genero genero,
        TipoDocumentoIdentidade documentoIdentidadeTipo,
        String documentoIdentidadeNumero,
        String documentoIdentidadeUf,
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf,
        CategoriaProfissional categoria,
        List<String> especialidades,
        String cnpj,
        String registroProfissional,
        String registroProfissionalUf
) {
}
