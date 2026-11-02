package com.quickcare.backend.perfil;

import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.StatusAtendimento;
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

/** Perfil de outro usuário, com o que cada um pode ver dele. */
@Service
@RequiredArgsConstructor
public class PerfilPublicoService {

    private final ProfissionalRepository profissionalRepository;
    private final PacienteRepository pacienteRepository;
    private final PropostaRepository propostaRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;

    /**
     * Qualquer usuário logado vê o profissional aprovado e não banido. Os
     * contatos de emergência, só o paciente cujo atendimento com ele já começou.
     */
    @Transactional(readOnly = true)
    public PerfilPublicoProfissionalResponse buscarProfissional(Long usuarioId, Long profissionalId) {
        Profissional profissional = profissionalRepository.findById(profissionalId)
                .filter(p -> !p.isBanido() && p.getStatusValidacao() == StatusValidacao.APROVADO)
                .orElseThrow(PerfilNaoEncontradoException::new);

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

    /** Os contatos de emergência são trocados enquanto um atendimento entre os dois está acontecendo. */
    private boolean atendimentoEmAndamento(Profissional profissional, Long pacienteId) {
        return atendimentoRepository.existsByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(
                profissional.getId(), pacienteId, Set.of(StatusAtendimento.EM_ANDAMENTO));
    }

    /** O profissional tem proposta aceita numa solicitação do paciente que está numa dessas fases. */
    private boolean atendimentoNaFase(Profissional profissional, Long pacienteId, Set<StatusSolicitacao> fases) {
        return propostaRepository.existsByProfissionalIdAndSolicitacaoPacienteIdAndStatusAndSolicitacaoStatusIn(
                profissional.getId(), pacienteId, StatusProposta.ACEITA, fases);
    }

    /**
     * Só o profissional com proposta aceita numa solicitação do paciente o vê —
     * antes disso nem o nome dele é revelado. Os dados de saúde, enquanto essa
     * solicitação estiver em curso; os contatos de emergência, só depois que o
     * atendimento começou.
     */
    @Transactional(readOnly = true)
    public PerfilPublicoPacienteResponse buscarPaciente(Long usuarioId, Long pacienteId) {
        Profissional profissional = profissionalRepository.findByUsuarioId(usuarioId)
                .filter(p -> !p.isBanido())
                .orElseThrow(PerfilNaoEncontradoException::new);

        if (!propostaRepository.existsByProfissionalIdAndSolicitacaoPacienteIdAndStatus(
                profissional.getId(), pacienteId, StatusProposta.ACEITA)) {
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
