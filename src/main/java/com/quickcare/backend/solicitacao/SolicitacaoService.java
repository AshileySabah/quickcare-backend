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
import com.quickcare.backend.solicitacao.dto.ProfissionalSolicitadoRequest;
import com.quickcare.backend.solicitacao.dto.SolicitacaoRequest;
import com.quickcare.backend.solicitacao.dto.SolicitacaoResponse;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoInvalidaException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoNaoEncontradaException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
     * Solicitações que ainda recebem propostas e pedem ao menos um profissional
     * que ele atende (mesma categoria e todas as especialidades da linha).
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
                .findDisponiveisPara(profissional.getCategoria(), especialidadeIds, StatusSolicitacao.RECEBENDO_PROPOSTAS_STATUS)
                .stream()
                .map(SolicitacaoResponse::de)
                .toList();
    }

    /**
     * Visível para o paciente dono e para profissionais que atendem alguma das
     * linhas pedidas. Para os demais responde "não encontrada", sem revelar que
     * a solicitação existe.
     */
    @Transactional(readOnly = true)
    public SolicitacaoResponse buscar(Long usuarioId, Long id) {
        Solicitacao solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(SolicitacaoNaoEncontradaException::new);

        boolean ehDono = solicitacao.getPaciente().getUsuario().getId().equals(usuarioId);
        boolean ehProfissionalQueAtende = profissionalRepository.findByUsuarioId(usuarioId)
                .map(profissional -> solicitacao.getProfissionais().stream()
                        .anyMatch(pedido -> pedido.atendidaPor(profissional.getCategoria(), idsDasEspecialidades(profissional))))
                .orElse(false);

        if (!ehDono && !ehProfissionalQueAtende) {
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
        aplicarProfissionais(solicitacao, request.profissionais());
        solicitacao.setDescricao(request.descricao().trim());
        solicitacao.setModalidade(request.modalidade());
        solicitacao.setPrazoDesejado(request.prazoDesejado());
        aplicarEndereco(solicitacao, request);
    }

    /** Valida todas as linhas antes de substituir as atuais (orphanRemoval apaga as antigas). */
    private void aplicarProfissionais(Solicitacao solicitacao, List<ProfissionalSolicitadoRequest> pedidos) {
        Set<Long> todosIds = pedidos.stream()
                .flatMap(pedido -> pedido.especialidadeIds().stream())
                .collect(Collectors.toSet());
        Map<Long, Especialidade> especialidades = especialidadeRepository.findAllByIdIn(List.copyOf(todosIds)).stream()
                .collect(Collectors.toMap(Especialidade::getId, Function.identity()));
        if (especialidades.size() != todosIds.size()) {
            throw new EspecialidadeInvalidaException("Uma ou mais especialidades informadas não existem.");
        }

        Set<Set<Long>> linhasVistas = new HashSet<>();
        List<SolicitacaoProfissional> novas = new ArrayList<>();

        for (ProfissionalSolicitadoRequest pedido : pedidos) {
            Set<Especialidade> especialidadesDaLinha = pedido.especialidadeIds().stream()
                    .map(especialidades::get)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            if (especialidadesDaLinha.stream().anyMatch(especialidade -> especialidade.getCategoria() != pedido.categoria())) {
                throw new SolicitacaoInvalidaException(
                        "As especialidades de cada profissional devem ser da categoria escolhida.");
            }
            if (!linhasVistas.add(Set.copyOf(pedido.especialidadeIds()))) {
                throw new SolicitacaoInvalidaException(
                        "Há profissionais repetidos com as mesmas especialidades. Aumente a quantidade na linha existente.");
            }

            novas.add(SolicitacaoProfissional.builder()
                    .solicitacao(solicitacao)
                    .categoria(pedido.categoria())
                    .especialidades(especialidadesDaLinha)
                    .quantidade(pedido.quantidade())
                    .build());
        }

        solicitacao.getProfissionais().clear();
        solicitacao.getProfissionais().addAll(novas);
    }

    /** Online não guarda endereço, mesmo que o front envie um. */
    private void aplicarEndereco(Solicitacao solicitacao, SolicitacaoRequest request) {
        boolean presencial = request.modalidade() == ModalidadeSolicitacao.PRESENCIAL;
        EnderecoSolicitacaoRequest endereco = presencial ? request.endereco() : null;

        if (presencial && endereco == null) {
            throw new SolicitacaoInvalidaException("Informe o endereço para atendimento presencial.");
        }

        solicitacao.setCep(endereco == null ? null : endereco.cep());
        solicitacao.setLogradouro(endereco == null ? null : endereco.logradouro().trim());
        solicitacao.setNumero(endereco == null ? null : endereco.numero().trim());
        solicitacao.setComplemento(endereco == null || endereco.complemento() == null || endereco.complemento().isBlank()
                ? null
                : endereco.complemento().trim());
        solicitacao.setBairro(endereco == null ? null : endereco.bairro().trim());
        solicitacao.setCidade(endereco == null ? null : endereco.cidade().trim());
        solicitacao.setUf(endereco == null ? null : validarUf(endereco.uf()));
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
