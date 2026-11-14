package com.quickcare.backend.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AutenticacaoPorToken autenticacaoPorToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            autenticacaoPorToken.autenticarCabecalho(request.getHeader(HttpHeaders.AUTHORIZATION)).ifPresent(sessao -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(sessao.usuarioId(), null, List.of()));
                if (sessao.tokenRenovado() != null) {
                    response.setHeader(AutenticacaoPorToken.HEADER_TOKEN, sessao.tokenRenovado());
                }
            });
        }

        filterChain.doFilter(request, response);
    }

}
