package com.quickcare.backend.administrador;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Admin criado na inicialização a partir de variáveis de ambiente, para
 * ambientes sem massa de dados (produção). Sem e-mail e senha, nada é criado.
 */
@ConfigurationProperties(prefix = "quickcare.admin-inicial")
public record AdministradorInicialProperties(
        String email,
        String senha,
        String nome,
        String cpf
) {

    public boolean configurado() {
        return email != null && !email.isBlank() && senha != null && !senha.isBlank();
    }

}
