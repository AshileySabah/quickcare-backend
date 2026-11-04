package com.quickcare.backend.validacao.dto;

import com.quickcare.backend.common.validation.Cnpj;
import com.quickcare.backend.common.validation.Cpf;
import com.quickcare.backend.usuario.Genero;
import com.quickcare.backend.usuario.TipoDocumentoIdentidade;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ReenvioEtapaRequest(
        @Size(max = 150) String nome,
        @Cpf @Pattern(regexp = "\\d{11}") String cpf,
        TipoDocumentoIdentidade documentoIdentidadeTipo,
        @Size(max = 20) String documentoIdentidadeNumero,
        @Pattern(regexp = "(?i)^[a-z]{2}$") String documentoIdentidadeUf,
        @Past LocalDate dataNascimento,
        Genero genero,
        @Cnpj @Pattern(regexp = "\\d{14}") String cnpj,
        @Size(max = 50) String registroProfissional,
        @Pattern(regexp = "(?i)^[a-z]{2}$") String registroProfissionalUf
) {
}
