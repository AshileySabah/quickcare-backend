package com.quickcare.backend.validacao.dto;

import com.quickcare.backend.common.validation.Cnpj;
import com.quickcare.backend.common.validation.Cpf;
import com.quickcare.backend.usuario.TipoDocumentoIdentidade;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados corrigidos de uma etapa reprovada. Só os campos da etapa reenviada são
 * lidos: identidade (nome, CPF, RG/CNH), CNPJ ou registro profissional.
 */
public record ReenvioEtapaRequest(
        @Size(max = 150) String nome,
        @Cpf @Pattern(regexp = "\\d{11}") String cpf,
        TipoDocumentoIdentidade documentoIdentidadeTipo,
        @Size(max = 20) String documentoIdentidadeNumero,
        @Pattern(regexp = "(?i)^[a-z]{2}$") String documentoIdentidadeUf,
        @Cnpj @Pattern(regexp = "\\d{14}") String cnpj,
        @Size(max = 50) String registroProfissional,
        @Pattern(regexp = "(?i)^[a-z]{2}$") String registroProfissionalUf
) {
}
