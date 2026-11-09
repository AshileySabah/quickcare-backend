package com.quickcare.backend.seguranca;

import com.quickcare.backend.banimento.BanimentoRepository;
import com.quickcare.backend.bloqueiousuario.BloqueioUsuarioRepository;
import com.quickcare.backend.denuncia.DenunciaRepository;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.ocorrencia.BloqueioRepository;
import com.quickcare.backend.ocorrencia.MotivoBloqueioTardioRepository;
import com.quickcare.backend.ocorrencia.OcorrenciaRepository;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HerancaPorDocumentoService {

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final BloqueioUsuarioRepository bloqueioUsuarioRepository;
    private final DenunciaRepository denunciaRepository;
    private final BanimentoRepository banimentoRepository;
    private final OcorrenciaRepository ocorrenciaRepository;
    private final BloqueioRepository bloqueioRepository;
    private final MotivoBloqueioTardioRepository motivoBloqueioRepository;
    private final PendenciasDoPerfil pendencias;
    private final Notificador notificador;
    private final Clock clock;

    @Transactional
    public void herdar(Usuario usuario, Paciente novo) {
        if (usuario.getCpf() == null) {
            return;
        }
        List<Paciente> antigos = pacienteRepository.findExcluidosDoCpf(usuario.getCpf()).stream()
                .filter(antigo -> !antigo.getId().equals(novo.getId()))
                .toList();
        if (antigos.isEmpty()) {
            return;
        }
        Instant agora = Instant.now(clock);
        Map<Long, Usuario> donos = donosAnteriores(usuario);
        for (Paciente antigo : antigos) {
            bloqueioUsuarioRepository.transferirPaciente(antigo, novo);
            denunciaRepository.transferirPaciente(antigo, novo);
            donos.putIfAbsent(antigo.getUsuario().getId(), antigo.getUsuario());
        }
        transferirRegistros(donos, usuario, TipoPerfil.PACIENTE);
        Instant congeladoEm = antigos.getFirst().getEncerradoEm();
        pendencias.prorrogar(usuario, TipoPerfil.PACIENTE, congeladoEm, Duration.between(congeladoEm, agora));
        novo.setBanido(pendencias.banido(usuario, TipoPerfil.PACIENTE, agora));
        avisar(usuario, TipoPerfil.PACIENTE, agora);
    }

    @Transactional
    public void herdar(Usuario usuario, Profissional novo) {
        if (usuario.getCpf() == null) {
            return;
        }
        List<Profissional> antigos = profissionalRepository.findExcluidosDoCpf(usuario.getCpf()).stream()
                .filter(antigo -> !antigo.getId().equals(novo.getId()))
                .toList();
        if (antigos.isEmpty()) {
            return;
        }
        Instant agora = Instant.now(clock);
        Map<Long, Usuario> donos = donosAnteriores(usuario);
        for (Profissional antigo : antigos) {
            bloqueioUsuarioRepository.transferirProfissional(antigo, novo);
            denunciaRepository.transferirProfissional(antigo, novo);
            donos.putIfAbsent(antigo.getUsuario().getId(), antigo.getUsuario());
        }
        transferirRegistros(donos, usuario, TipoPerfil.PROFISSIONAL);
        Instant congeladoEm = antigos.getFirst().getEncerradoEm();
        pendencias.prorrogar(usuario, TipoPerfil.PROFISSIONAL, congeladoEm, Duration.between(congeladoEm, agora));
        novo.setBanido(pendencias.banido(usuario, TipoPerfil.PROFISSIONAL, agora));
        avisar(usuario, TipoPerfil.PROFISSIONAL, agora);
    }

    private Map<Long, Usuario> donosAnteriores(Usuario usuario) {
        Map<Long, Usuario> donos = new LinkedHashMap<>();
        usuarioRepository.findExcluidosDoCpf(usuario.getCpf()).forEach(antigo -> donos.put(antigo.getId(), antigo));
        return donos;
    }

    private void transferirRegistros(Map<Long, Usuario> donos, Usuario usuario, TipoPerfil perfil) {
        donos.values().stream()
                .filter(antigo -> !antigo.getId().equals(usuario.getId()))
                .forEach(antigo -> {
                    banimentoRepository.transferir(antigo, usuario, perfil);
                    ocorrenciaRepository.transferir(antigo, usuario, perfil);
                    bloqueioRepository.transferir(antigo, usuario, perfil);
                    motivoBloqueioRepository.transferir(antigo, usuario, perfil);
                });
    }

    private void avisar(Usuario usuario, TipoPerfil perfil, Instant agora) {
        List<String> descricao = pendencias.descrever(usuario, perfil, agora);
        if (!descricao.isEmpty()) {
            notificador.pendenciasDoDocumento(usuario, perfil, descricao);
        }
    }

}
