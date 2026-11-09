package com.quickcare.backend.common.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MaiorDeIdadeValidatorTest {

    private record Pessoa(@MaiorDeIdade(message = "O paciente deve ter pelo menos 18 anos.") LocalDate dataNascimento) {
    }

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void iniciar() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void encerrar() {
        factory.close();
    }

    @Test
    void quemFaz18AnosHojeJaPodeUsar() {
        assertThat(validator.validate(new Pessoa(LocalDate.now().minusYears(18)))).isEmpty();
    }

    @Test
    void faltandoUmDiaPara18AnosERecusadoComAMensagemDoPerfil() {
        assertThat(validator.validate(new Pessoa(LocalDate.now().minusYears(18).plusDays(1))))
                .singleElement()
                .satisfies(erro -> assertThat(erro.getMessage()).isEqualTo("O paciente deve ter pelo menos 18 anos."));
    }

    @Test
    void dataDeHojeOuFuturaERecusada() {
        assertThat(validator.validate(new Pessoa(LocalDate.now()))).hasSize(1);
        assertThat(validator.validate(new Pessoa(LocalDate.now().plusDays(3)))).hasSize(1);
    }

    @Test
    void semDataFicaParaONotNull() {
        assertThat(validator.validate(new Pessoa(null))).isEmpty();
    }

    @Test
    void regraDosDezoitoAnosConsideraOAniversario() {
        LocalDate hoje = LocalDate.of(2026, 11, 8);

        assertThat(Maioridade.atingida(LocalDate.of(2008, 11, 8), hoje)).isTrue();
        assertThat(Maioridade.atingida(LocalDate.of(2008, 11, 9), hoje)).isFalse();
    }
}
