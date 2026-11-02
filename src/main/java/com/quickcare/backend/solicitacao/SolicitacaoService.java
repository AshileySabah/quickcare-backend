package com.quickcare.backend.solicitacao;

import com.quickcare.backend.cadastro.exception.EspecialidadeInvalidaException;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.especialidade.EspecialidadeRepository;
import com.quickcare.backend.geolocalizacao.Coordenadas;
import com.quickcare.backend.geolocalizacao.EnderecoGeocodificavel;
import com.quickcare.backend.geolocalizacao.GeocodificacaoService;
import com.quickcare.backend.localizacao.Uf;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.MotivoReaceite;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.PropostaRepository.ContagemPropostas;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.dto.EnderecoSolicitacaoRequest;
import com.quickcare.backend.solicitacao.dto.ProfissionalSolicitadoRequest;
import com.quickcare.backend.solicitacao.dto.SolicitacaoRequest;
import com.quickcare.backend.solicitacao.dto.SolicitacaoResponse;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoInvalidaException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoNaoEncontradaException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.validacao.StatusValidacao;
import com.quickcare.backend.validacao.exception.CadastroNaoValidadoException;
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
    private final PropostaRepository propostaRepository;
    private final GeocodificacaoService geocodificacaoService;
    private final Notificador notificador;

    @Transactional
    public SolicitacaoResponse criar(Long usuarioId, SolicitacaoRequest request) {
        Paciente paciente = buscarPacienteAtivo(usuarioId);

        if (paciente.getStatusValidacao() != StatusValidacao.APROVADO) {
            throw new CadastroNaoValidadoException(
                    "Seu cadastro ainda não foi aprovado. Você poderá criar solicitações assim que ele for validado.");
        }

        Solicitacao solicitacao = Solicitacao.builder()
                .paciente(paciente)
                .status(StatusSolicitacao.ABERTA)
                .build();
        aplicarDados(solicitacao, request);

        return SolicitacaoResponse.de(solicitacaoRepository.save(solicitacao), 0);
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoResponse> listarDoPaciente(Long usuarioId) {
        Paciente paciente = buscarPacienteAtivo(usuarioId);

        return responder(solicitacaoRepository.findByPacienteIdOrderByCriadoEmDesc(paciente.getId()));
    }

    /**
     * Solicitações que ainda recebem propostas, numa modalidade que o
     * profissional atende, e que pedem ao menos um profissional da categoria
     * dele com todas as especialidades da linha entre as suas. As que já têm
     * proposta ativa dele saem daqui e ficam em "Minhas propostas"; com a
     * proposta recusada ou cancelada, voltam (ele pode enviar outra).
     */
    @Transactional(readOnly = true)
    public List<SolicitacaoResponse> listarDisponiveis(Long usuarioId) {
        Profissional profissional = profissionalRepository.findByUsuarioId(usuarioId)
                .filter(p -> !p.isBanido())
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Apenas profissionais podem ver solicitações disponíveis."));

        if (profissional.getStatusValidacao() != StatusValidacao.APROVADO) {
            throw new CadastroNaoValidadoException("Seu cadastro ainda não foi aprovado.");
        }

        Set<Long> especialidadeIds = idsDasEspecialidades(profissional);

        if (especialidadeIds.isEmpty()) {
            return List.of();
        }

        List<Solicitacao> candidatas = solicitacaoRepository.findDisponiveisPara(
                profissional.getCategoria(),
                especialidadeIds,
                ModalidadeSolicitacao.atendidasPor(profissional.getModalidadeAtendimento()),
                StatusSolicitacao.RECEBENDO_PROPOSTAS_STATUS);

        Set<Long> comProposta = propostaRepository.findSolicitacaoIdsComPropostaDoProfissional(
                profissional.getId(), StatusProposta.ATIVAS);

        // A distância até o endereço presencial é conferida na entidade. Quem
        // também é paciente não vê as próprias solicitações.
        return responder(candidatas.stream()
                .filter(solicitacao -> !solicitacao.ehDoUsuario(usuarioId))
                .filter(solicitacao -> !comProposta.contains(solicitacao.getId()))
                .filter(solicitacao -> solicitacao.atendidaPor(profissional))
                .toList());
    }

    /**
     * Visível para o paciente dono, para profissionais compatíveis e para quem
     * já enviou proposta (mesmo depois de a solicitação sair de "recebendo
     * propostas"). Para os demais responde "não encontrada", sem revelar que a
     * solicitação existe.
     */
    @Transactional(readOnly = true)
    public SolicitacaoResponse buscar(Long usuarioId, Long id) {
        Solicitacao solicitacao = solicitacaoRepository.findById(id)
                .orElseThrow(SolicitacaoNaoEncontradaException::new);

        boolean ehDono = solicitacao.ehDoUsuario(usuarioId);
        boolean ehProfissionalEnvolvido = profissionalRepository.findByUsuarioId(usuarioId)
                .map(profissional -> solicitacao.atendidaPor(profissional)
                        || propostaRepository.existsBySolicitacaoIdAndProfissionalIdAndStatusNot(
                                id, profissional.getId(), StatusProposta.CANCELADA))
                .orElse(false);

        if (!ehDono && !ehProfissionalEnvolvido) {
            throw new SolicitacaoNaoEncontradaException();
        }

        return responder(solicitacao);
    }

    @Transactional
    public SolicitacaoResponse atualizar(Long usuarioId, Long id, SolicitacaoRequest request) {
        Solicitacao solicitacao = buscarDoPaciente(usuarioId, id);

        if (!solicitacao.getStatus().podeSerEditada()) {
            throw new TransicaoStatusInvalidaException("Esta solicitação não pode mais ser editada.");
        }

        // Editar substitui as linhas pedidas: as propostas aceitas saem das
        // vagas e voltam a aguardar o aceite do paciente na versão nova.
        List<Proposta> aceitas = propostaRepository.findBySolicitacaoIdAndStatus(id, StatusProposta.ACEITA);
        aceitas.forEach(proposta -> proposta.voltarParaReaceite(MotivoReaceite.SOLICITACAO_ALTERADA));
        if (!aceitas.isEmpty()) {
            solicitacao.setStatus(StatusSolicitacao.RECEBENDO_PROPOSTAS);
        }

        List<Proposta> pendentes = propostaRepository.findBySolicitacaoIdAndStatus(id, StatusProposta.PENDENTE).stream()
                .filter(proposta -> !aceitas.contains(proposta))
                .toList();

        aplicarDados(solicitacao, request);
        SolicitacaoResponse response = responder(solicitacaoRepository.saveAndFlush(solicitacao));
        notificador.solicitacaoAlterada(aceitas, pendentes);
        return response;
    }

    /** Propostas ainda pendentes são recusadas junto com o cancelamento. */
    @Transactional
    public SolicitacaoResponse cancelar(Long usuarioId, Long id) {
        Solicitacao solicitacao = buscarDoPaciente(usuarioId, id);

        if (!solicitacao.getStatus().podeSerCancelada()) {
            throw new TransicaoStatusInvalidaException("Esta solicitação não pode mais ser cancelada.");
        }

        List<Proposta> pendentes = propostaRepository.findBySolicitacaoIdAndStatus(id, StatusProposta.PENDENTE);
        List<Proposta> aceitas = propostaRepository.findBySolicitacaoIdAndStatus(id, StatusProposta.ACEITA);
        pendentes.forEach(proposta -> proposta.setStatus(StatusProposta.RECUSADA));
        solicitacao.setStatus(StatusSolicitacao.CANCELADA);

        SolicitacaoResponse response = responder(solicitacaoRepository.saveAndFlush(solicitacao));
        notificador.solicitacaoCancelada(concatenar(aceitas, pendentes));
        return response;
    }

    @Transactional
    public SolicitacaoResponse concluir(Long usuarioId, Long id) {
        Solicitacao solicitacao = buscarDoPaciente(usuarioId, id);

        if (solicitacao.getStatus() != StatusSolicitacao.EM_ANDAMENTO) {
            throw new TransicaoStatusInvalidaException("Apenas solicitações em andamento podem ser concluídas.");
        }

        solicitacao.setStatus(StatusSolicitacao.CONCLUIDA);
        SolicitacaoResponse response = responder(solicitacaoRepository.saveAndFlush(solicitacao));
        notificador.solicitacaoConcluida(propostaRepository.findBySolicitacaoIdAndStatus(id, StatusProposta.ACEITA));
        return response;
    }

    private static List<Proposta> concatenar(List<Proposta> primeiras, List<Proposta> outras) {
        List<Proposta> todas = new ArrayList<>(primeiras);
        todas.addAll(outras);
        return todas;
    }

    private SolicitacaoResponse responder(Solicitacao solicitacao) {
        return responder(List.of(solicitacao)).get(0);
    }

    /** Uma única consulta de contagem de propostas para a lista toda. */
    private List<SolicitacaoResponse> responder(List<Solicitacao> solicitacoes) {
        if (solicitacoes.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> totalPorSolicitacao = propostaRepository
                .contarPorSolicitacao(solicitacoes.stream().map(Solicitacao::getId).toList(), StatusProposta.CANCELADA)
                .stream()
                .collect(Collectors.toMap(ContagemPropostas::getSolicitacaoId, ContagemPropostas::getTotal));

        return solicitacoes.stream()
                .map(solicitacao -> SolicitacaoResponse.de(
                        solicitacao, totalPorSolicitacao.getOrDefault(solicitacao.getId(), 0L)))
                .toList();
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

        Coordenadas coordenadas = endereco == null ? null : geocodificacaoService.geocodificar(new EnderecoGeocodificavel(
                solicitacao.getCep(), solicitacao.getLogradouro(), solicitacao.getNumero(),
                solicitacao.getCidade(), solicitacao.getUf())).orElse(null);
        solicitacao.setLatitude(coordenadas == null ? null : coordenadas.latitude());
        solicitacao.setLongitude(coordenadas == null ? null : coordenadas.longitude());
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
