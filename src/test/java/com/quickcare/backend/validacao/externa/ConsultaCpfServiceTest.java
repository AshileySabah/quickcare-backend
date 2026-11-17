package com.quickcare.backend.validacao.externa;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultaCpfServiceTest {

    @Test
    void semTokenNaoConsultaASerproEAvisaQueAValidacaoEManual() {
        ValidacaoExternaProperties properties = new ValidacaoExternaProperties(1,
                new ValidacaoExternaProperties.Cpf("http://127.0.0.1:1", " "),
                new ValidacaoExternaProperties.Cnpj("http://127.0.0.1:1"));
        ConsultaCpfService service = new ConsultaCpfService(properties, RestClient.builder());

        VerificacaoExterna verificacao = service.verificar("40442820135", "Rone Pereira", LocalDate.of(1970, 11, 14));

        assertThat(verificacao.parecer()).isEqualTo(Parecer.SEM_API);
        assertThat(verificacao.motivos()).singleElement().asString().contains("SERPRO_CPF_TOKEN");
        assertThat(verificacao.observacoes()).containsExactly("Valide o CPF manualmente.");
    }
}
