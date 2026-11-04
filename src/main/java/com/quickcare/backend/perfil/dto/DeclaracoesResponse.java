package com.quickcare.backend.perfil.dto;

public record DeclaracoesResponse(
        boolean confirmacaoInformacoesVerdadeiras,
        boolean consentimentoLgpd
) {
}
