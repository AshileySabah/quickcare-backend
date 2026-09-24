package com.quickcare.backend.especialidade;

public enum CategoriaProfissional {
    MEDICO("Médico(a)", "CRM"),
    ENFERMEIRO("Enfermeiro(a)", "COREN"),
    CUIDADOR("Cuidador(a)", null),
    OUTRO("Outros profissionais", "Registro profissional");

    private final String rotulo;
    private final String siglaRegistro;

    CategoriaProfissional(String rotulo, String siglaRegistro) {
        this.rotulo = rotulo;
        this.siglaRegistro = siglaRegistro;
    }

    public String getRotulo() {
        return rotulo;
    }

    public String getSiglaRegistro() {
        return siglaRegistro;
    }
}
