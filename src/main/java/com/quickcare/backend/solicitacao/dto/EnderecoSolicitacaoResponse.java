package com.quickcare.backend.solicitacao.dto;

public record EnderecoSolicitacaoResponse(
        String cep,
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String cidade,
        String uf
) {
}
