package com.quickcare.backend.solicitacao;

import com.quickcare.backend.cadastro.exception.EspecialidadeInvalidaException;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.especialidade.EspecialidadeRepository;
import com.quickcare.backend.localizacao.Uf;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.dto.EnderecoSolicitacaoRequest;
import com.quickcare.backend.solicitacao.dto.SolicitacaoRequest;
import com.quickcare.backend.solicitacao.dto.SolicitacaoResponse;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoInvalidaException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoNaoEncontradaException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SolicitacaoService {

    private final SolicitacaoRepository solicitacaoRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final EspecialidadeRepository especialidadeRepository;

    @Transactional
    public SolicitacaoResponse criar(Long usuarioId, SolicitacaoRequest request) {
        Paciente paciente = buscarPacienteAtivo(usuarioId);

        Solicitacao solicitacao = Solicitacao.builder()
                .paciente(paciente)
                .status(StatusSolicitacao.ABERTA)
                .build();
        aplicarDados(solicitacao, request);

        return SolicitacaoResponse.de(solicitacaoRepository.save(solicitacao));
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoResponse> listarDoPaciente(Long usuarioId) {
        Paciente paciente = buscarPacienteAtivo(usuarioId);

        return solicitacaoRepository.findByPacienteIdOrderByCriadoEmDesc(paciente.getId()).stream()
                .map(SolicitacaoResponse::de)
                .toList();
    }

    /**
     * Solicitações que ainda recebem propostas nas especialidades do profissional.
     */
    @Transactional(readOnly = true)
    public List<SolicitacaoResponse> listarDisponiveis(Long usuarioId) {
        Profissional profissional = profissionalRepository.findByUsuarioId(usuarioId)
                .filter(p -> !p.isBanido())
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Apenas profissionais podem ver solicitações disponíveis."));

        Set<Long> especialidadeIds = idsDasEspecialidades(profissional);

        if (especialidadeIds.isEmpty()) {
            return List.of();
        }

        return solicitacaoRepository
                .findByEspecialidadeIdInAndStatusInOrderByCriadoEmDesc(especialidadeIds, StatusSolicitacao.RECEBENDO_PROPOSTAS_STATUS)
                .stream()
                .map(SolicitacaoResponse::de)
                .toList();
    }

    /**
     * Visível para o paciente dono e para profissionais da especialidade. Para os
     * demais responde "não encontrada", sem revelar que a solicitação existe.
     */
    @Transactional(readOnly = true)
    public SolicitacaoResponse buscar(Long usuarioId, Long id) {
        Solicitacao solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(SolicitacaoNaoEncontradaException::new);

        boolean ehDono = solicitacao.getPaciente().getUsuario().getId().equals(usuarioId);
        boolean ehProfissionalDaEspecialidade = profissionalRepository.findByUsuarioId(usuarioId)
                .map(profissional -> idsDasEspecialidades(profissional).contains(solicitacao.getEspecialidade().getId()))
                .orElse(false);

        if (!ehDono && !ehProfissionalDaEspecialidade) {
            throw new SolicitacaoNaoEncontradaException();
        }

        return SolicitacaoResponse.de(solicitacao);
    }

    @Transactional
    public SolicitacaoResponse atualizar(Long usuarioId, Long id, SolicitacaoRequest request) {
        Solicitacao solicitacao = buscarDoPaciente(usuarioId, id);

        if (!solicitacao.getStatus().podeSerEditada()) {
            throw new TransicaoStatusInvalidaException("Esta solicitação não pode mais ser editada.");
        }

        aplicarDados(solicitacao, request);
        return SolicitacaoResponse.de(solicitacaoRepository.saveAndFlush(solicitacao));
    }

    @Transactional
    public SolicitacaoResponse cancelar(Long usuarioId, Long id) {
        Solicitacao solicitacao = buscarDoPaciente(usuarioId, id);

        if (!solicitacao.getStatus().podeSerCancelada()) {
            throw new TransicaoStatusInvalidaException("Esta solicitação não pode mais ser cancelada.");
        }

        solicitacao.setStatus(StatusSolicitacao.CANCELADA);
        return SolicitacaoResponse.de(solicitacaoRepository.saveAndFlush(solicitacao));
    }

    @Transactional
    public SolicitacaoResponse concluir(Long usuarioId, Long id) {
        Solicitacao solicitacao = buscarDoPaciente(usuarioId, id);

        if (solicitacao.getStatus() != StatusSolicitacao.EM_ANDAMENTO) {
            throw new TransicaoStatusInvalidaException("Apenas solicitações em andamento podem ser concluídas.");
        }

        solicitacao.setStatus(StatusSolicitacao.CONCLUIDA);
        return SolicitacaoResponse.de(solicitacaoRepository.saveAndFlush(solicitacao));
    }

    private void aplicarDados(Solicitacao solicitacao, SolicitacaoRequest request) {
        Especialidade especialidade = especialidadeRepository.findById(request.especialidadeId())
                .orElseThrow(() -> new EspecialidadeInvalidaException("A especialidade informada não existe."));

        solicitacao.setEspecialidade(especialidade);
        solicitacao.setDescricao(request.descricao().trim());
        solicitacao.setModalidade(request.modalidade());
        solicitacao.setPrazoDesejado(request.prazoDesejado());

        if (request.modalidade() == ModalidadeSolicitacao.PRESENCIAL) {
            EnderecoSolicitacaoRequest endereco = request.endereco();
            if (endereco == null) {
                throw new SolicitacaoInvalidaException("Informe o endereço para atendimento presencial.");
            }
            solicitacao.setLogradouro(endereco.logradouro().trim());
            solicitacao.setCidade(endereco.cidade().trim());
            solicitacao.setUf(validarUf(endereco.uf()));
        } else {
            solicitacao.setLogradouro(null);
            solicitacao.setCidade(null);
            solicitacao.setUf(null);
        }
    }

    private String validarUf(String uf) {
        try {
            return Uf.valueOf(uf.toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new SolicitacaoInvalidaException("UF inválida.");
        }
    }

    private Paciente buscarPacienteAtivo(Long usuarioId) {
        return pacienteRepository.findByUsuarioId(usuarioId)
                .filter(paciente -> !paciente.isBanido())
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Apenas pacientes podem gerenciar solicitações."));
    }

    private Solicitacao buscarDoPaciente(Long usuarioId, Long id) {
        Paciente paciente = buscarPacienteAtivo(usuarioId);
        return solicitacaoRepository.findByIdAndPacienteId(id, paciente.getId())
                .orElseThrow(SolicitacaoNaoEncontradaException::new);
    }

    private Set<Long> idsDasEspecialidades(Profissional profissional) {
        return profissional.getEspecialidades().stream()
                .map(Especialidade::getId)
                .collect(Collectors.toSet());
    }

}
