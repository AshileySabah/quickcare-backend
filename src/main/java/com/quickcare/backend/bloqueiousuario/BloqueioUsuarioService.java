package com.quickcare.backend.bloqueiousuario;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.bloqueiousuario.dto.BloqueioUsuarioResponse;
import com.quickcare.backend.bloqueiousuario.dto.PreviaBloqueioResponse;
import com.quickcare.backend.common.RecursoNaoEncontradoException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.validacao.StatusValidacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * O paciente bloqueia um profissional (qualquer aprovado: pelo perfil, vindo
 * de uma proposta ou da consulta pública); o profissional bloqueia um paciente
 * com quem já teve atendimento. Vale nos dois sentidos e encerra o que estava
 * vigente entre eles ({@link AtendimentoService#encerrarPorBloqueio}).
 */
@Service
@RequiredArgsConstructor
public class BloqueioUsuarioService {

    private final BloqueioUsuarioRepository bloqueioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final PropostaRepository propostaRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final AtendimentoService atendimentoService;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;
    private final Clock clock;

    // ================================================ paciente → profissional

    @Transactional(readOnly = true)
    public PreviaBloqueioResponse previaDoProfissional(Long usuarioId, Long profissionalId) {
        Paciente paciente = pacienteAtivo(usuarioId);
        Profissional profissional = profissionalBloqueavel(usuarioId, profissionalId);
        return previa(paciente, profissional, profissional.getUsuario().getNome());
    }

    @Transactional
    public BloqueioUsuarioResponse bloquearProfissional(Long usuarioId, Long profissionalId) {
        Paciente paciente = pacienteAtivo(usuarioId);
        Profissional profissional = profissionalBloqueavel(usuarioId, profissionalId);
        return doProfissional(bloquear(paciente, profissional, TipoPerfil.PACIENTE));
    }

    // ================================================ profissional → paciente

    @Transactional(readOnly = true)
    public PreviaBloqueioResponse previaDoPaciente(Long usuarioId, Long pacienteId) {
        Profissional profissional = profissionalAtivo(usuarioId);
        Paciente paciente = pacienteBloqueavel(profissional, pacienteId);
        return previa(paciente, profissional, paciente.getUsuario().getNome());
    }

    @Transactional
    public BloqueioUsuarioResponse bloquearPaciente(Long usuarioId, Long pacienteId) {
        Profissional profissional = profissionalAtivo(usuarioId);
        Paciente paciente = pacienteBloqueavel(profissional, pacienteId);
        return doPaciente(bloquear(paciente, profissional, TipoPerfil.PROFISSIONAL));
    }

    // ===================================================== lista e desbloqueio

    /** Quem eu bloqueei com este perfil, mais recentes primeiro. */
    @Transactional(readOnly = true)
    public List<BloqueioUsuarioResponse> listar(Long usuarioId, TipoPerfil perfil) {
        if (perfil == TipoPerfil.PACIENTE) {
            return pacienteRepository.findByUsuarioId(usuarioId)
                    .map(paciente -> bloqueioRepository.findByPacienteIdAndBloqueadoPorOrderByCriadoEmDesc(
                            paciente.getId(), TipoPerfil.PACIENTE).stream().map(this::doProfissional).toList())
                    .orElse(List.of());
        }
        return profissionalRepository.findByUsuarioId(usuarioId)
                .map(profissional -> bloqueioRepository.findByProfissionalIdAndBloqueadoPorOrderByCriadoEmDesc(
                        profissional.getId(), TipoPerfil.PROFISSIONAL).stream().map(this::doPaciente).toList())
                .orElse(List.of());
    }

    /** Só quem bloqueou desfaz. O que foi encerrado no bloqueio não volta. */
    @Transactional
    public void desbloquear(Long usuarioId, Long id) {
        BloqueioUsuario bloqueio = bloqueioRepository.findById(id)
                .filter(encontrado -> (encontrado.getBloqueadoPor() == TipoPerfil.PACIENTE
                        ? encontrado.getPaciente().getUsuario()
                        : encontrado.getProfissional().getUsuario()).getId().equals(usuarioId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bloqueio não encontrado."));
        bloqueioRepository.delete(bloqueio);
    }

    // ================================================================= apoio

    private BloqueioUsuario bloquear(Paciente paciente, Profissional profissional, TipoPerfil quemBloqueia) {
        if (bloqueioRepository.findByPacienteIdAndProfissionalIdAndBloqueadoPor(
                paciente.getId(), profissional.getId(), quemBloqueia).isPresent()) {
            throw new TransicaoStatusInvalidaException("Você já bloqueou esta pessoa.");
        }
        atendimentoService.encerrarPorBloqueio(paciente.getId(), profissional.getId(), lado(quemBloqueia));
        return bloqueioRepository.save(BloqueioUsuario.builder()
                .paciente(paciente)
                .profissional(profissional)
                .bloqueadoPor(quemBloqueia)
                .build());
    }

    private PreviaBloqueioResponse previa(Paciente paciente, Profissional profissional, String nome) {
        Instant agora = Instant.now(clock);
        List<Atendimento> vigentes = atendimentoRepository.findByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(
                profissional.getId(), paciente.getId(), StatusAtendimento.ATIVOS);
        int pendentes = propostaRepository.findByProfissionalIdAndSolicitacaoPacienteIdAndStatus(
                profissional.getId(), paciente.getId(), StatusProposta.PENDENTE).size();
        boolean contaOcorrencia = vigentes.stream()
                .anyMatch(atendimento -> AtendimentoService.ocorrenciaDoCancelamento(atendimento, agora).isPresent());
        return new PreviaBloqueioResponse(nome, pendentes, vigentes.size(), contaOcorrencia);
    }

    private Paciente pacienteAtivo(Long usuarioId) {
        return pacienteRepository.findByUsuarioId(usuarioId)
                .filter(paciente -> !paciente.isBanido())
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Apenas pacientes podem bloquear profissionais."));
    }

    private Profissional profissionalAtivo(Long usuarioId) {
        return profissionalRepository.findByUsuarioId(usuarioId)
                .filter(profissional -> !profissional.isBanido())
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Apenas profissionais podem bloquear pacientes."));
    }

    /** O mesmo profissional que o paciente vê no perfil público; nunca o próprio usuário. */
    private Profissional profissionalBloqueavel(Long usuarioId, Long profissionalId) {
        return profissionalRepository.findById(profissionalId)
                .filter(profissional -> profissional.getStatusValidacao() == StatusValidacao.APROVADO)
                .filter(profissional -> !profissional.getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional não encontrado."));
    }

    /** Só o paciente com quem já teve atendimento (é quando o profissional passa a vê-lo). */
    private Paciente pacienteBloqueavel(Profissional profissional, Long pacienteId) {
        if (!atendimentoRepository.existsByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(
                profissional.getId(), pacienteId, Set.of(StatusAtendimento.values()))) {
            throw new RecursoNaoEncontradoException("Paciente não encontrado.");
        }
        return pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado."));
    }

    private static LadoAtendimento lado(TipoPerfil perfil) {
        return perfil == TipoPerfil.PACIENTE ? LadoAtendimento.PACIENTE : LadoAtendimento.PROFISSIONAL;
    }

    private BloqueioUsuarioResponse doProfissional(BloqueioUsuario bloqueio) {
        Profissional profissional = bloqueio.getProfissional();
        return new BloqueioUsuarioResponse(bloqueio.getId(), profissional.getId(), profissional.getUsuario().getNome(),
                armazenamentoDocumentoService.urlDoAvatar(profissional.getUsuario()), bloqueio.getCriadoEm());
    }

    private BloqueioUsuarioResponse doPaciente(BloqueioUsuario bloqueio) {
        Paciente paciente = bloqueio.getPaciente();
        return new BloqueioUsuarioResponse(bloqueio.getId(), paciente.getId(), paciente.getUsuario().getNome(),
                armazenamentoDocumentoService.urlDoAvatar(paciente.getUsuario()), bloqueio.getCriadoEm());
    }

}
