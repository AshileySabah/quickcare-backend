package com.quickcare.backend.cadastro.dto;

import com.quickcare.backend.common.validation.Cpf;
import com.quickcare.backend.common.validation.MaiorDeIdade;
import com.quickcare.backend.usuario.Genero;
import com.quickcare.backend.usuario.TipoDocumentoIdentidade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.quickcare.backend.common.validation.SenhaForte;

import java.time.LocalDate;
import java.util.List;

public record CadastroPacienteRequest(
        @NotBlank(message = "Informe o nome completo.") String nome,
        @NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido.") String email,
        @NotBlank(message = "Crie uma senha.") @SenhaForte String senha,
        @NotBlank(message = "Informe o CPF.") @Pattern(regexp = "\\d{11}", message = "O CPF deve ter 11 dígitos.") @Cpf String cpf,
        String telefone,
        @NotNull(message = "Escolha o tipo do documento de identidade.") TipoDocumentoIdentidade documentoIdentidadeTipo,
        @NotBlank(message = "Informe o número do documento de identidade.")
        @Size(max = 20, message = "O número do documento deve ter no máximo 20 caracteres.") String documentoIdentidadeNumero,
        @Pattern(regexp = "(?i)^[a-z]{2}$", message = "Escolha a UF que emitiu o documento.") String documentoIdentidadeUf,
        @NotNull(message = "O preenchimento da data de nascimento é obrigatório.")
        @MaiorDeIdade(message = "O paciente deve ter pelo menos 18 anos.") LocalDate dataNascimento,
        @NotNull(message = "Escolha o gênero.") Genero genero,
        @Valid List<ContatoEmergenciaRequest> contatosEmergencia,
        @AssertTrue(message = "Confirme que as informações são verdadeiras.") boolean confirmacaoInformacoesVerdadeiras,
        @AssertTrue(message = "Para continuar, aceite o tratamento dos seus dados conforme a LGPD.") boolean consentimentoLgpd,
        String alergias,
        String condicoesSaude,
        String medicamentosEmUso,
        @NotBlank(message = "Informe o CEP.") @Pattern(regexp = "\\d{8}", message = "O CEP deve ter 8 dígitos.") String cep,
        @NotBlank(message = "Informe o logradouro.") String logradouro,
        @NotBlank(message = "Informe o número do endereço.") String numero,
        String complemento,
        @NotBlank(message = "Informe o bairro.") String bairro,
        @NotBlank(message = "Informe a cidade.") String cidade,
        @NotBlank(message = "Informe a UF do endereço.") @Pattern(regexp = "(?i)^[a-z]{2}$", message = "A UF deve ter 2 letras.") String uf
) {

    public NovoPerfilPacienteRequest perfil() {
        return new NovoPerfilPacienteRequest(dataNascimento, genero, contatosEmergencia,
                confirmacaoInformacoesVerdadeiras, consentimentoLgpd, alergias, condicoesSaude, medicamentosEmUso,
                cep, logradouro, numero, complemento, bairro, cidade, uf);
    }

}
