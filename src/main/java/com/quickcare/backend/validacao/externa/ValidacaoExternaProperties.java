package com.quickcare.backend.validacao.externa;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "quickcare.validacao-externa")
public record ValidacaoExternaProperties(
        int timeoutSegundos,
        Cpf cpf,
        Cnpj cnpj
) {

    public record Cpf(String url, String token) {
    }

    public record Cnpj(String url) {
    }

}
