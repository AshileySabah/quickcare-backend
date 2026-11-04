package com.quickcare.backend.geolocalizacao;

import java.math.BigDecimal;

public record Coordenadas(BigDecimal latitude, BigDecimal longitude) {

    private static final double RAIO_DA_TERRA_KM = 6371.0;

    public static Coordenadas de(BigDecimal latitude, BigDecimal longitude) {
        return latitude == null || longitude == null ? null : new Coordenadas(latitude, longitude);
    }

    public double distanciaKm(Coordenadas outra) {
        double lat1 = Math.toRadians(latitude.doubleValue());
        double lat2 = Math.toRadians(outra.latitude.doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(outra.longitude.doubleValue() - longitude.doubleValue());

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * RAIO_DA_TERRA_KM * Math.asin(Math.sqrt(a));
    }

}
