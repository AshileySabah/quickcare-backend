package com.quickcare.backend.validacao.externa;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * APIs de terceiros usadas na pré-validação do admin.
 * <ul>
 *   <li>CPF: Serpro Consulta CPF. Por padrão aponta para o ambiente de
 *   demonstração (token público, só conhece CPFs fictícios); em produção basta
 *   trocar url e token pelos do contrato.</li>
 *   <li>CNPJ: BrasilAPI, gratuita e sem chave, com dados públicos da Receita.</li>
 * </ul>
 */
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
