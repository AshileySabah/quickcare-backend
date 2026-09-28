package com.quickcare.backend.solicitacao.dto;

public record EnderecoSolicitacaoResponse(
        String logradouro,
        String cidade,
        String uf
) {
}
