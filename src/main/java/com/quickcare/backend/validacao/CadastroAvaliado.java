package com.quickcare.backend.validacao;

import com.quickcare.backend.documento.Documento;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.usuario.Genero;
import com.quickcare.backend.usuario.Usuario;

import java.time.Instant;
import java.time.LocalDate;

public record CadastroAvaliado(Paciente paciente, Profissional profissional) {

    public static CadastroAvaliado de(Paciente paciente) {
        return new CadastroAvaliado(paciente, null);
    }

    public static CadastroAvaliado de(Profissional profissional) {
        return new CadastroAvaliado(null, profissional);
    }

    public PerfilValidado perfil() {
        return paciente != null ? PerfilValidado.PACIENTE : PerfilValidado.PROFISSIONAL;
    }

    public Long id() {
        return paciente != null ? paciente.getId() : profissional.getId();
    }

    public Usuario usuario() {
        return paciente != null ? paciente.getUsuario() : profissional.getUsuario();
    }

    public StatusValidacao statusValidacao() {
        return paciente != null ? paciente.getStatusValidacao() : profissional.getStatusValidacao();
    }

    public void definirStatusValidacao(StatusValidacao status) {
        if (paciente != null) {
            paciente.setStatusValidacao(status);
        } else {
            profissional.setStatusValidacao(status);
        }
    }

    public Instant criadoEm() {
        return paciente != null ? paciente.getCriadoEm() : profissional.getCriadoEm();
    }

    public LocalDate dataNascimento() {
        return paciente != null ? paciente.getDataNascimento() : profissional.getDataNascimento();
    }

    public Genero genero() {
        return paciente != null ? paciente.getGenero() : profissional.getGenero();
    }

    public void definirDadosPessoais(LocalDate dataNascimento, Genero genero) {
        if (paciente != null) {
            paciente.setDataNascimento(dataNascimento);
            paciente.setGenero(genero);
        } else {
            profissional.setDataNascimento(dataNascimento);
            profissional.setGenero(genero);
        }
    }

    public EtapaValidacao novaEtapaPendente(TipoEtapaValidacao tipo, Documento documento) {
        return EtapaValidacao.pendente(tipo, documento, paciente, profissional);
    }

}
