package com.quickcare.backend.seguranca.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodigoEmailRequest(
        @NotBlank(message = "Informe o novo e-mail.") @Email(message = "Informe um e-mail válido.")
        @Size(max = 255, message = "O e-mail pode ter no máximo 255 caracteres.") String novoEmail,
        @NotBlank(message = "Informe sua senha atual.") String senha
) {
}
