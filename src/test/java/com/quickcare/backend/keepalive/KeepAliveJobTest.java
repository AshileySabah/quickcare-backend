package com.quickcare.backend.keepalive;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeepAliveJobTest {

    private static final String URL = "https://quickcare.onrender.com";

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    @Test
    void pingaOHealthCheckDaUrlPublicaSemConsultarOBanco() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(URL + KeepAliveJob.HEALTH_PATH))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess());

        new KeepAliveJob(URL, builder, jdbcTemplate).pingarServico();

        servidor.verify();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void falhaNoPingNaoPropagaErro() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(URL + KeepAliveJob.HEALTH_PATH)).andRespond(withServerError());

        KeepAliveJob job = new KeepAliveJob(URL, builder, jdbcTemplate);

        assertThatCode(job::pingarServico).doesNotThrowAnyException();
        servidor.verify();
    }

    @Test
    void semUrlNaoFazRequisicao() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();

        new KeepAliveJob(" ", builder, jdbcTemplate).pingarServico();

        servidor.verify();
    }

    @Test
    void consultaOBanco() {
        new KeepAliveJob(URL, RestClient.builder(), jdbcTemplate).consultarBanco();

        verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
    }

    @Test
    void falhaNoBancoNaoPropagaErro() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("banco fora"));

        KeepAliveJob job = new KeepAliveJob(URL, RestClient.builder(), jdbcTemplate);

        assertThatCode(job::consultarBanco).doesNotThrowAnyException();
    }
}
