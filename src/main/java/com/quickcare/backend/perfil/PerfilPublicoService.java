package com.quickcare.backend.perfil;

import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.bloqueiousuario.BloqueioUsuarioRepository;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.perfil.dto.ContatoEmergenciaResponse;
import com.quickcare.backend.perfil.dto.PerfilPublicoPacienteResponse;
import com.quickcare.backend.perfil.dto.PerfilPublicoProfissionalResponse;
import com.quickcare.backend.perfil.exception.PerfilNaoEncontradoException;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.validacao.StatusValidacao;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PerfilPublicoService {

    private final ProfissionalRepository profissionalRepository;
    private final PacienteRepository pacienteRepository;
    private final PropostaRepository propostaRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;
    private final BloqueioUsuarioRepository bloqueioUsuarioRepository;

    @Transactional(readOnly = true)
    public PerfilPublicoProfissionalResponse buscarProfissional(Long usuarioId, Long profissionalId) {
        Profissional profissional = profissionalRepository.findById(profissionalId)
                .filter(p -> !p.isBanido() && p.getStatusValidacao() == StatusValidacao.APROVADO)
                .orElseThrow(PerfilNaoEncontradoException::new);
        Long pacienteId = pacienteRepository.findByUsuarioId(usuarioId).map(Paciente::getId).orElse(null);
        if (pacienteId != null && bloqueioUsuarioRepository.existsByPacienteIdAndProfissionalId(pacienteId, profissional.getId())) {
            throw new PerfilNaoEncontradoException();
        }

        return new PerfilPublicoProfissionalResponse(
                profissional.getId(),
                profissional.getUsuario().getNome(),
                armazenamentoDocumentoService.urlDoAvatar(profissional.getUsuario()),
                profissional.getCategoria(),
                profissional.getCategoria().getRotulo(),
                profissional.getEspecialidades().stream().map(Especialidade::getNome).sorted().toList(),
                profissional.getCategoria().getSiglaRegistro(),
                profissional.getRegistroProfissional(),
                profissional.getRegistroProfissionalUf(),
                profissional.getModalidadeAtendimento(),
                profissional.getCidade(),
                profissional.getUf(),
                profissional.getCriadoEm(),
                atendimentoRepository.countByPropostaProfissionalIdAndStatusIn(
                        profissional.getId(), StatusAtendimento.REALIZADOS),
                atendimentoIniciado(usuarioId, profissional)
                        ? ContatoEmergenciaResponse.de(profissional.getContatosEmergencia())
                        : null
        );
    }

    private boolean atendimentoIniciado(Long usuarioId, Profissional profissional) {
        return pacienteRepository.findByUsuarioId(usuarioId)
                .map(paciente -> atendimentoEmAndamento(profissional, paciente.getId()))
                .orElse(false);
    }

    private boolean atendimentoEmAndamento(Profissional profissional, Long pacienteId) {
        return atendimentoRepository.existsByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(
                profissional.getId(), pacienteId, Set.of(StatusAtendimento.EM_ANDAMENTO));
    }

    private boolean atendimentoNaFase(Profissional profissional, Long pacienteId, Set<StatusSolicitacao> fases) {
        return propostaRepository.existsByProfissionalIdAndSolicitacaoPacienteIdAndStatusAndSolicitacaoStatusIn(
                profissional.getId(), pacienteId, StatusProposta.ACEITA, fases);
    }

    @Transactional(readOnly = true)
    public PerfilPublicoPacienteResponse buscarPaciente(Long usuarioId, Long pacienteId) {
        Profissional profissional = profissionalRepository.findByUsuarioId(usuarioId)
                .filter(p -> !p.isBanido())
                .orElseThrow(PerfilNaoEncontradoException::new);

        if (!propostaRepository.existsByProfissionalIdAndSolicitacaoPacienteIdAndStatus(
                profissional.getId(), pacienteId, StatusProposta.ACEITA)
                || bloqueioUsuarioRepository.existsByPacienteIdAndProfissionalId(pacienteId, profissional.getId())) {
            throw new PerfilNaoEncontradoException();
        }

        Paciente paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(PerfilNaoEncontradoException::new);

        return new PerfilPublicoPacienteResponse(
                paciente.getId(),
                paciente.getUsuario().getNome(),
                armazenamentoDocumentoService.urlDoAvatar(paciente.getUsuario()),
                Period.between(paciente.getDataNascimento(), LocalDate.now()).getYears(),
                paciente.getCidade(),
                paciente.getUf(),
                paciente.getCriadoEm(),
                atendimentoNaFase(profissional, pacienteId, StatusSolicitacao.EM_CURSO)
                        ? new PerfilPublicoPacienteResponse.Saude(
                                paciente.getAlergias(), paciente.getCondicoesSaude(), paciente.getMedicamentosEmUso())
                        : null,
                atendimentoEmAndamento(profissional, pacienteId)
                        ? ContatoEmergenciaResponse.de(paciente.getContatosEmergencia())
                        : null
        );
    }

}
