package com.quickcare.backend.cadastro.dto;

import com.quickcare.backend.atuacao.dto.AtuacaoRequest;
import com.quickcare.backend.common.validation.Cnpj;
import com.quickcare.backend.common.validation.Cpf;
import com.quickcare.backend.profissional.ModalidadeAtendimento;
import com.quickcare.backend.usuario.Genero;
import com.quickcare.backend.usuario.TipoDocumentoIdentidade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.quickcare.backend.common.validation.SenhaForte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CadastroProfissionalRequest(
        @NotBlank String nome,
        @NotBlank @Email String email,
        @NotBlank @SenhaForte String senha,
        @NotBlank @Pattern(regexp = "\\d{11}") @Cpf String cpf,
        String telefone,
        @NotNull TipoDocumentoIdentidade documentoIdentidadeTipo,
        @NotBlank @Size(max = 20) String documentoIdentidadeNumero,
        @Pattern(regexp = "(?i)^[a-z]{2}$") String documentoIdentidadeUf,
        @Pattern(regexp = "\\d{14}") @Cnpj String cnpj,
        @NotEmpty @Valid List<AtuacaoRequest> atuacoes,
        @NotNull @Past LocalDate dataNascimento,
        @NotNull Genero genero,
        @Valid List<ContatoEmergenciaRequest> contatosEmergencia,
        @AssertTrue boolean confirmacaoInformacoesVerdadeiras,
        @AssertTrue boolean consentimentoLgpd,
        boolean possuiSeguroResponsabilidadeCivil,
        @NotNull ModalidadeAtendimento modalidadeAtendimento,
        @Positive BigDecimal raioAtendimentoKm,
        @NotBlank @Pattern(regexp = "\\d{8}") String cep,
        @NotBlank String logradouro,
        @NotBlank String numero,
        String complemento,
        @NotBlank String bairro,
        @NotBlank String cidade,
        @NotBlank @Pattern(regexp = "(?i)^[a-z]{2}$") String uf
) {

    public NovoPerfilProfissionalRequest perfil() {
        return new NovoPerfilProfissionalRequest(cnpj, atuacoes, dataNascimento, genero, contatosEmergencia,
                confirmacaoInformacoesVerdadeiras, consentimentoLgpd, possuiSeguroResponsabilidadeCivil,
                modalidadeAtendimento, raioAtendimentoKm, cep, logradouro, numero, complemento, bairro, cidade, uf);
    }

}
