package com.quickcare.backend.auth.dto;

import com.quickcare.backend.common.validation.SenhaForte;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.quickcare.backend.common.validation.SenhaForte;
import lombok.Data;

@Data
public class RedefinirSenhaRequest {

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    private String email;

    @NotBlank(message = "Código é obrigatório")
    private String token;

    @NotBlank(message = "Nova senha é obrigatória")
    @SenhaForte
    private String novaSenha;
}