package com.quickcare.backend.validacao;

import com.quickcare.backend.validacao.exception.CadastroNaoEncontradoException;

/** Converte o segmento da URL ("paciente(s)"/"profissional(is)") no perfil. */
final class PerfilNaUrl {

    private PerfilNaUrl() {
    }

    static PerfilValidado ler(String valor) {
        return switch (valor) {
            case "paciente", "pacientes" -> PerfilValidado.PACIENTE;
            case "profissional", "profissionais" -> PerfilValidado.PROFISSIONAL;
            default -> throw new CadastroNaoEncontradoException();
        };
    }

}
