package com.quickcare.backend.jwt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private AutenticacaoPorToken autenticacao;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        autenticacao = mock(AutenticacaoPorToken.class);
        filter = new JwtAuthenticationFilter(autenticacao);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void autenticaPeloCabecalhoAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        request.addHeader("Authorization", "Bearer tok");
        when(autenticacao.autenticarCabecalho("Bearer tok"))
                .thenReturn(Optional.of(new AutenticacaoPorToken.Sessao(7L, null)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(7L);
        assertThat(response.getHeader(AutenticacaoPorToken.HEADER_TOKEN)).isNull();
    }

    @Test
    void devolveOTokenRenovadoNoCabecalhoDaResposta() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/notificacoes");
        request.addHeader("Authorization", "Bearer antigo");
        when(autenticacao.autenticarCabecalho("Bearer antigo"))
                .thenReturn(Optional.of(new AutenticacaoPorToken.Sessao(7L, "novo")));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(AutenticacaoPorToken.HEADER_TOKEN)).isEqualTo("novo");
    }

    @Test
    void semTokenValidoSegueSemAutenticar() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/me");
        when(autenticacao.autenticarCabecalho(null)).thenReturn(Optional.empty());

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

}
