package com.quickcare.backend.validacao.externa;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvaliacaoConsultasExternasTest {

    private static final ConsultaCpfService.RespostaSerpro RONE = new ConsultaCpfService.RespostaSerpro(
            "40442820135", "Rone Pereira", new ConsultaCpfService.Situacao("0", "Regular"), "14111970");

    @Test
    void cpfRegularComNomeENascimentoConferindoEhFavoravel() {
        VerificacaoExterna resultado = ConsultaCpfService.avaliar(RONE, "  rone   PEREIRA ", LocalDate.of(1970, 11, 14));

        assertThat(resultado.parecer()).isEqualTo(Parecer.FAVORAVEL);
        assertThat(resultado.motivos()).anyMatch(motivo -> motivo.contains("Nome confere"));
    }

    @Test
    void nomeComAcentoDiferenteAindaConfere() {
        ConsultaCpfService.RespostaSerpro joao = new ConsultaCpfService.RespostaSerpro(
                "1", "JOAO CONCEICAO", new ConsultaCpfService.Situacao("0", "Regular"), "01011990");

        assertThat(ConsultaCpfService.avaliar(joao, "João Conceição", LocalDate.of(1990, 1, 1)).parecer())
                .isEqualTo(Parecer.FAVORAVEL);
    }

    @Test
    void cpfDeOutraPessoaEhDesfavoravelExplicandoADivergencia() {
        VerificacaoExterna resultado = ConsultaCpfService.avaliar(RONE, "Maria Souza", LocalDate.of(1990, 1, 1));

        assertThat(resultado.parecer()).isEqualTo(Parecer.DESFAVORAVEL);
        assertThat(resultado.motivos()).hasSize(2)
                .anyMatch(motivo -> motivo.startsWith("Nome diverge"))
                .anyMatch(motivo -> motivo.startsWith("Data de nascimento diverge"));
    }

    @Test
    void cpfCanceladoEhDesfavoravel() {
        ConsultaCpfService.RespostaSerpro cancelado = new ConsultaCpfService.RespostaSerpro(
                "01648527949", "Matheus Lima", new ConsultaCpfService.Situacao("5", "Cancelada por Multiplicidade"), "18021996");

        VerificacaoExterna resultado = ConsultaCpfService.avaliar(cancelado, "Matheus Lima", LocalDate.of(1996, 2, 18));

        assertThat(resultado.parecer()).isEqualTo(Parecer.DESFAVORAVEL);
        assertThat(resultado.motivos()).containsExactly("Situação cadastral irregular: Cancelada por Multiplicidade.");
    }

    @Test
    void cnpjAtivoEhFavoravelEInformaSeOProfissionalEhSocio() {
        ConsultaCnpjService.RespostaBrasilApi clinica = new ConsultaCnpjService.RespostaBrasilApi(
                "CLINICA XYZ LTDA", "", "ATIVA", List.of(new ConsultaCnpjService.Socio("MARIA SOUZA")));

        VerificacaoExterna resultado = ConsultaCnpjService.avaliar(clinica, "Maria Souza");

        assertThat(resultado.parecer()).isEqualTo(Parecer.FAVORAVEL);
        assertThat(resultado.observacoes()).contains("Maria Souza consta no quadro de sócios.");
    }

    @Test
    void cnpjBaixadoEhDesfavoravel() {
        ConsultaCnpjService.RespostaBrasilApi baixada = new ConsultaCnpjService.RespostaBrasilApi(
                "EMPRESA ANTIGA LTDA", null, "BAIXADA", List.of());

        VerificacaoExterna resultado = ConsultaCnpjService.avaliar(baixada, "Maria Souza");

        assertThat(resultado.parecer()).isEqualTo(Parecer.DESFAVORAVEL);
        assertThat(resultado.observacoes()).anyMatch(observacao -> observacao.contains("não consta no quadro de sócios"));
    }

    @Test
    void parecerGeralPriorizaDesfavoravelDepoisIndisponivel() {
        assertThat(Parecer.geral(List.of(Parecer.FAVORAVEL, Parecer.SEM_API))).isEqualTo(Parecer.FAVORAVEL);
        assertThat(Parecer.geral(List.of(Parecer.FAVORAVEL, Parecer.INDISPONIVEL))).isEqualTo(Parecer.INDISPONIVEL);
        assertThat(Parecer.geral(List.of(Parecer.INDISPONIVEL, Parecer.DESFAVORAVEL))).isEqualTo(Parecer.DESFAVORAVEL);
        assertThat(Parecer.geral(List.of(Parecer.SEM_API))).isEqualTo(Parecer.SEM_API);
    }

}
