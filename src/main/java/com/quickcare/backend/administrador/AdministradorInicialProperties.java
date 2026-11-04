package com.quickcare.backend.administrador;

import org.springframework.boot.context.properties.ConfigurationProperties;

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
