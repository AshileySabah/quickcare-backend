package com.quickcare.backend.denuncia.dto;

import com.quickcare.backend.denuncia.MotivoDenuncia;
import com.quickcare.backend.usuario.TipoPerfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DenunciaRequest(
        @NotNull(message = "Não foi possível identificar o seu perfil. Recarregue a página e tente de novo.") TipoPerfil perfil,
        @NotNull(message = "Não foi possível identificar quem você quer denunciar. Recarregue a página e tente de novo.") Long denunciadoId,
        @NotNull(message = "Escolha o motivo da denúncia.") MotivoDenuncia motivo,
        @NotBlank(message = "Conte o que aconteceu.")
        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres.") String descricao
) {
}
