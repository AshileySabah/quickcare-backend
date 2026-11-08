package com.quickcare.backend.validacao.dto;

import com.quickcare.backend.common.validation.Cnpj;
import com.quickcare.backend.common.validation.Cpf;
import com.quickcare.backend.usuario.Genero;
import com.quickcare.backend.usuario.TipoDocumentoIdentidade;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ReenvioEtapaRequest(
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.") String nome,
        @Cpf @Pattern(regexp = "\\d{11}", message = "O CPF deve ter 11 dígitos.") String cpf,
        TipoDocumentoIdentidade documentoIdentidadeTipo,
        @Size(max = 20, message = "O número do documento deve ter no máximo 20 caracteres.") String documentoIdentidadeNumero,
        @Pattern(regexp = "(?i)^[a-z]{2}$", message = "Escolha a UF que emitiu o documento.") String documentoIdentidadeUf,
        LocalDate dataNascimento,
        Genero genero,
        @Cnpj @Pattern(regexp = "\\d{14}", message = "O CNPJ deve ter 14 dígitos.") String cnpj,
        @Size(max = 50, message = "O número do registro deve ter no máximo 50 caracteres.") String registroProfissional,
        @Pattern(regexp = "(?i)^[a-z]{2}$", message = "Escolha a UF do registro profissional.") String registroProfissionalUf
) {
}
