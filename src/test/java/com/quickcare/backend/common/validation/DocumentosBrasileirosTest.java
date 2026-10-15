package com.quickcare.backend.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentosBrasileirosTest {

    @ParameterizedTest
    @ValueSource(strings = {"52998224725", "11144477735", "40442820135"})
    void aceitaCpfComDigitosCorretos(String cpf) {
        assertThat(DocumentosBrasileiros.cpfValido(cpf)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"52998224724", "11111111111", "1234567890", "5299822472a"})
    void recusaCpfInvalido(String cpf) {
        assertThat(DocumentosBrasileiros.cpfValido(cpf)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000191", "11222333000181"})
    void aceitaCnpjComDigitosCorretos(String cnpj) {
        assertThat(DocumentosBrasileiros.cnpjValido(cnpj)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"00000000000192", "11111111111111", "1122233300018"})
    void recusaCnpjInvalido(String cnpj) {
        assertThat(DocumentosBrasileiros.cnpjValido(cnpj)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"02650306461", "12345678900", "24681357982"})
    void aceitaCnhComDigitosCorretos(String cnh) {
        assertThat(DocumentosBrasileiros.cnhValida(cnh)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"02650306462", "00000000000", "2650306461"})
    void recusaCnhInvalida(String cnh) {
        assertThat(DocumentosBrasileiros.cnhValida(cnh)).isFalse();
    }

    @Test
    void rgDeSpConfereDigitoVerificadorInclusiveX() {
        assertThat(DocumentosBrasileiros.rgValido("246781312", "SP")).isTrue();
        assertThat(DocumentosBrasileiros.rgValido("246781313", "SP")).isFalse();
        assertThat(DocumentosBrasileiros.rgValido("10000006X", "sp")).isTrue();
        assertThat(DocumentosBrasileiros.rgValido("10000006x", "SP")).isTrue();
    }

    @Test
    void rgDeOutrosEstadosConfereSoOFormato() {
        assertThat(DocumentosBrasileiros.rgValido("1234567", "MG")).isTrue();
        assertThat(DocumentosBrasileiros.rgValido("12345678X", "RJ")).isTrue();
        assertThat(DocumentosBrasileiros.rgValido("123", "MG")).isFalse();
        assertThat(DocumentosBrasileiros.rgValido("12A45678", "MG")).isFalse();
    }

}
