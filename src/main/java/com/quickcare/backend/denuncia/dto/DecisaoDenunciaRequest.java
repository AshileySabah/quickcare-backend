package com.quickcare.backend.denuncia.dto;

import com.quickcare.backend.banimento.TipoBanimento;
import com.quickcare.backend.denuncia.ConsequenciaDenuncia;
import com.quickcare.backend.denuncia.StatusDenuncia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecisaoDenunciaRequest(
        @NotNull(message = "Escolha se a denúncia é procedente ou improcedente.") StatusDenuncia resultado,
        ConsequenciaDenuncia consequencia,
        @NotBlank(message = "Escreva a justificativa da decisão.")
        @Size(max = 1000, message = "A justificativa deve ter no máximo 1000 caracteres.") String justificativa,
        TipoBanimento tipoBanimento,
        Integer diasSuspensao
) {

    public DecisaoDenunciaRequest(StatusDenuncia resultado, ConsequenciaDenuncia consequencia, String justificativa) {
        this(resultado, consequencia, justificativa, null, null);
    }

}
