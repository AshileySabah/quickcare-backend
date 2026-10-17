package com.quickcare.backend.geolocalizacao;

public record EnderecoGeocodificavel(
        String cep,
        String logradouro,
        String numero,
        String cidade,
        String uf
) {
}
