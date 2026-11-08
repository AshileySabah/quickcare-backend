package com.quickcare.backend.proposta;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.bloqueiousuario.BloqueioUsuarioRepository;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.ocorrencia.OcorrenciaService;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.dto.PropostaRequest;
import com.quickcare.backend.proposta.dto.PropostaResponse;
import com.quickcare.backend.proposta.exception.PropostaJaEnviadaException;
import com.quickcare.backend.proposta.exception.PropostaNaoEncontradaException;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoRepository;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoInvalidaException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoNaoEncontradaException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.validacao.StatusValidacao;
import com.quickcare.backend.validacao.ValidacaoService;
import com.quickcare.backend.validacao.exception.CadastroNaoValidadoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PropostaService {

    private static final Duration DURACAO_MAXIMA = Duration.ofDays(366);

    private final PropostaRepository propostaRepository;
    private final SolicitacaoRepository solicitacaoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;
    private final AtendimentoService atendimentoService;
    private final AtendimentoRepository atendimentoRepository;
    private final Notificador notificador;
    private final OcorrenciaService ocorrenciaService;
    private final BloqueioUsuarioRepository bloqueioUsuarioRepository;
    private final ValidacaoService validacaoService;
    private final Clock clock;

    @Transactional
    public PropostaResponse enviar(Long usuarioId, Long solicitacaoId, PropostaRequest request) {
        Profissional profissional = buscarProfissionalAtivo(usuarioId);
        if (profissional.getStatusValidacao() != StatusValidacao.APROVADO) {
            throw new CadastroNaoValidadoException("Só profissionais com cadastro aprovado podem enviar propostas.");
        }
        validacaoService.exigirLiberadoParaCriar(profissional);
        ocorrenciaService.exigirSemBloqueio(usuarioId, TipoPerfil.PROFISSIONAL);
        List<Atuacao> liberadas = validacaoService.atuacoesLiberadas(profissional);
        Solicitacao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .filter(s -> !s.ehDoUsuario(usuarioId))
                .filter(s -> s.atendidaPor(profissional, profissional.atuacoesAtivas()))
                .orElseThrow(SolicitacaoNaoEncontradaException::new);

        if (!solicitacao.getStatus().recebePropostas() || bloqueados(solicitacao, profissional)) {
            throw new TransicaoStatusInvalidaException("Esta solicitação não está mais recebendo propostas.");
        }
        Atuacao atuacao = escolherAtuacao(solicitacao, profissional, liberadas, request.atuacaoId());

        Proposta proposta = Proposta.builder()
                .solicitacao(solicitacao)
                .profissional(profissional)
                .atuacao(atuacao)
                .status(StatusProposta.PENDENTE)
                .build();
        aplicarDados(proposta, request);

        if (solicitacao.getStatus() == StatusSolicitacao.ABERTA) {
            solicitacao.setStatus(StatusSolicitacao.RECEBENDO_PROPOSTAS);
        }

        Proposta salva = propostaRepository.save(proposta);
        notificador.propostaRecebida(salva);
        return responder(salva, usuarioId);
    }

    @Transactional(readOnly = true)
    public List<PropostaResponse> listarDaSolicitacao(Long usuarioId, Long solicitacaoId) {
        atendimentoService.processarVencidos();
        Solicitacao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(SolicitacaoNaoEncontradaException::new);

        if (solicitacao.ehDoUsuario(usuarioId)) {
            return responder(propostaRepository.findBySolicitacaoIdOrderByCriadoEmAsc(solicitacaoId).stream()
                    .filter(proposta -> proposta.getStatus() != StatusProposta.CANCELADA)
                    .toList(), usuarioId);
        }

        Profissional profissional = profissionalRepository.findByUsuarioId(usuarioId)
                .orElseThrow(SolicitacaoNaoEncontradaException::new);
        List<Proposta> minhas = propostaRepository
                .findBySolicitacaoIdAndProfissionalIdOrderByCriadoEmAsc(solicitacaoId, profissional.getId());

        if (minhas.isEmpty() && !solicitacao.atendidaPor(profissional, validacaoService.atuacoesLiberadas(profissional))) {
            throw new SolicitacaoNaoEncontradaException();
        }

        return responder(minhas, usuarioId);
    }

    @Transactional(readOnly = true)
    public List<PropostaResponse> listarDoProfissional(Long usuarioId) {
        atendimentoService.processarVencidos();
        Profissional profissional = profissionalRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Apenas profissionais podem gerenciar propostas."));
        return responder(propostaRepository.findByProfissionalIdOrderByCriadoEmDesc(profissional.getId()), usuarioId);
    }

    @Transactional(readOnly = true)
    public PropostaResponse buscar(Long usuarioId, Long id) {
        atendimentoService.processarVencidos();
        Proposta proposta = propostaRepository.findById(id)
                .filter(p -> p.ehDoUsuario(usuarioId) || p.getSolicitacao().ehDoUsuario(usuarioId))
                .orElseThrow(PropostaNaoEncontradaException::new);

        return responder(proposta, usuarioId);
    }

    @Transactional
    public PropostaResponse atualizar(Long usuarioId, Long id, PropostaRequest request) {
        Proposta proposta = buscarAlteravelDoProfissional(usuarioId, id, "editada");
        validacaoService.exigirAtuacaoLiberada(proposta.getAtuacao());
        boolean estavaAceita = proposta.getStatus() == StatusProposta.ACEITA;

        if (estavaAceita) {
            atendimentoService.cancelarPorAlteracaoDaProposta(proposta);
            proposta.voltarParaReaceite(MotivoReaceite.PROPOSTA_ALTERADA);
        }

        aplicarDados(proposta, request);
        Proposta salva = propostaRepository.saveAndFlush(proposta);
        recalcularSolicitacao(salva.getSolicitacao());
        notificador.propostaAlterada(salva, estavaAceita);
        return responder(salva, usuarioId);
    }

    @Transactional
    public PropostaResponse cancelar(Long usuarioId, Long id) {
        Proposta proposta = buscarAlteravelDoProfissional(usuarioId, id, "cancelada");
        encerrarPeloProfissional(proposta);
        return responder(proposta, usuarioId);
    }

    @Transactional
    public void encerrarPeloProfissional(Proposta proposta) {
        if (proposta.getStatus() == StatusProposta.ACEITA) {
            atendimentoService.cancelarPeloProfissional(proposta);
        } else {
            proposta.setStatus(StatusProposta.CANCELADA);
            propostaRepository.saveAndFlush(proposta);
            recalcularSolicitacao(proposta.getSolicitacao());
            notificador.propostaCancelada(proposta, false);
        }
    }

    private Atuacao escolherAtuacao(Solicitacao solicitacao, Profissional profissional, List<Atuacao> liberadas,
                                    Long atuacaoId) {
        List<Atuacao> candidatas = solicitacao.atuacoesQueAtendem(profissional, liberadas).stream()
                .filter(candidata -> !propostaRepository.existsBySolicitacaoIdAndAtuacaoIdAndStatusIn(
                        solicitacao.getId(), candidata.getId(), StatusProposta.ATIVAS))
                .toList();

        if (atuacaoId != null) {
            return candidatas.stream()
                    .filter(candidata -> candidata.getId().equals(atuacaoId))
                    .findFirst()
                    .orElseThrow(() -> atuacaoIndisponivel(solicitacao, profissional, atuacaoId));
        }
        if (candidatas.size() == 1) {
            return candidatas.getFirst();
        }
        if (candidatas.isEmpty()) {
            throw semAtuacaoDisponivel(solicitacao, profissional);
        }
        throw new SolicitacaoInvalidaException("Escolha para qual vaga da solicitação é esta proposta.");
    }

    private RuntimeException atuacaoIndisponivel(Solicitacao solicitacao, Profissional profissional, Long atuacaoId) {
        boolean jaEnviada = propostaRepository.existsBySolicitacaoIdAndAtuacaoIdAndStatusIn(
                solicitacao.getId(), atuacaoId, StatusProposta.ATIVAS);
        if (jaEnviada) {
            return new PropostaJaEnviadaException();
        }
        return profissional.atuacoesAtivas().stream()
                .filter(atuacao -> atuacao.getId().equals(atuacaoId))
                .findFirst()
                .<RuntimeException>map(atuacao -> validacaoService.atuacaoLiberada(atuacao)
                        ? new SolicitacaoInvalidaException("Esta solicitação não tem vaga aberta para a atuação escolhida.")
                        : new CadastroNaoValidadoException("O seu registro de " + atuacao.rotulo()
                                + " ainda está em validação. Até o administrador aprovar, você não pode enviar propostas dela."))
                .orElseGet(() -> new SolicitacaoInvalidaException("Atuação inválida para esta proposta."));
    }

    private RuntimeException semAtuacaoDisponivel(Solicitacao solicitacao, Profissional profissional) {
        boolean temPropostaAtiva = solicitacao.atuacoesQueAtendem(profissional, profissional.atuacoesAtivas()).stream()
                .anyMatch(atuacao -> propostaRepository.existsBySolicitacaoIdAndAtuacaoIdAndStatusIn(
                        solicitacao.getId(), atuacao.getId(), StatusProposta.ATIVAS));
        if (temPropostaAtiva) {
            return new PropostaJaEnviadaException();
        }
        return new CadastroNaoValidadoException("O seu registro da atuação desta vaga ainda está em validação. "
                + "Até o administrador aprovar, você não pode enviar propostas dela.");
    }

    @Transactional
    public PropostaResponse recusar(Long usuarioId, Long id) {
        Proposta proposta = buscarDoPaciente(usuarioId, id);

        if (!proposta.getStatus().pendente() || !proposta.getSolicitacao().getStatus().recebePropostas()) {
            throw new TransicaoStatusInvalidaException("Esta proposta não pode mais ser recusada.");
        }

        proposta.setStatus(StatusProposta.RECUSADA);
        propostaRepository.saveAndFlush(proposta);
        recalcularSolicitacao(proposta.getSolicitacao());
        notificador.propostaRecusada(proposta, false);

        return responder(proposta, usuarioId);
    }

    @Transactional
    public PropostaResponse aceitar(Long usuarioId, Long id) {
        Proposta proposta = buscarDoPaciente(usuarioId, id);

        if (!proposta.getStatus().pendente() || !proposta.getSolicitacao().getStatus().recebePropostas()
                || bloqueados(proposta.getSolicitacao(), proposta.getProfissional())) {
            throw new TransicaoStatusInvalidaException("Esta proposta não está mais disponível para aceite.");
        }

        atendimentoService.aceitarProposta(proposta);
        Proposta salva = propostaRepository.saveAndFlush(proposta);
        notificador.propostaAceita(salva);
        return responder(salva, usuarioId);
    }

    private boolean bloqueados(Solicitacao solicitacao, Profissional profissional) {
        return bloqueioUsuarioRepository.existsByPacienteIdAndProfissionalId(
                solicitacao.getPaciente().getId(), profissional.getId());
    }

    private void recalcularSolicitacao(Solicitacao solicitacao) {
        solicitacao.recalcularStatus(
                propostaRepository.existsBySolicitacaoIdAndStatus(solicitacao.getId(), StatusProposta.PENDENTE));
    }

    private PropostaResponse responder(Proposta proposta, Long usuarioId) {
        return responder(List.of(proposta), usuarioId).get(0);
    }

    private List<PropostaResponse> responder(List<Proposta> propostas, Long usuarioId) {
        if (propostas.isEmpty()) {
            return List.of();
        }
        Map<Long, Atendimento> atendimentoPorProposta = atendimentoRepository
                .findByPropostaIdInOrderByIdAsc(propostas.stream().map(Proposta::getId).toList()).stream()
                .collect(Collectors.toMap(atendimento -> atendimento.getProposta().getId(), Function.identity(),
                        (anterior, maisRecente) -> maisRecente));
        Instant agora = Instant.now(clock);
        Map<List<Long>, Boolean> bloqueadosPorPar = new HashMap<>();

        return propostas.stream()
                .map(proposta -> PropostaResponse.de(proposta, atendimentoPorProposta.get(proposta.getId()), agora,
                        armazenamentoDocumentoService::urlDoAvatar, ocultar(proposta, usuarioId, bloqueadosPorPar)))
                .toList();
    }

    private PropostaResponse.Ocultar ocultar(Proposta proposta, Long usuarioId, Map<List<Long>, Boolean> bloqueadosPorPar) {
        boolean bloqueados = bloqueadosPorPar.computeIfAbsent(
                List.of(proposta.getSolicitacao().getPaciente().getId(), proposta.getProfissional().getId()),
                par -> bloqueioUsuarioRepository.existsByPacienteIdAndProfissionalId(par.get(0), par.get(1)));
        if (!bloqueados) {
            return PropostaResponse.Ocultar.NINGUEM;
        }
        return proposta.ehDoUsuario(usuarioId) ? PropostaResponse.Ocultar.PACIENTE : PropostaResponse.Ocultar.PROFISSIONAL;
    }

    private void aplicarDados(Proposta proposta, PropostaRequest request) {
        if (!request.termino().isAfter(request.inicio())) {
            throw new SolicitacaoInvalidaException("O término precisa ser depois do início.");
        }
        if (Duration.between(request.inicio(), request.termino()).compareTo(DURACAO_MAXIMA) > 0) {
            throw new SolicitacaoInvalidaException("O atendimento pode durar no máximo um ano.");
        }
        proposta.setValor(request.valor());
        proposta.setAbordagem(request.abordagem().trim());
        proposta.setInicio(request.inicio());
        proposta.setTermino(request.termino());
    }

    private Profissional buscarProfissionalAtivo(Long usuarioId) {
        return profissionalRepository.findByUsuarioId(usuarioId)
                .filter(profissional -> !profissional.isBanido())
                .orElseThrow(() -> new PerfilNaoAutorizadoException("Apenas profissionais podem gerenciar propostas."));
    }

    private Proposta buscarDoPaciente(Long usuarioId, Long id) {
        Proposta proposta = propostaRepository.findById(id)
                .filter(p -> p.getSolicitacao().ehDoUsuario(usuarioId))
                .orElseThrow(PropostaNaoEncontradaException::new);

        if (proposta.getSolicitacao().getPaciente().isBanido()) {
            throw new PerfilNaoAutorizadoException("Apenas pacientes podem responder propostas.");
        }
        return proposta;
    }

    private Proposta buscarAlteravelDoProfissional(Long usuarioId, Long id, String acao) {
        Proposta proposta = buscarDoProfissional(usuarioId, id);

        if (!proposta.getStatus().podeSerAlterada()
                || !StatusSolicitacao.EM_CURSO.contains(proposta.getSolicitacao().getStatus())) {
            throw new TransicaoStatusInvalidaException("Esta proposta não pode mais ser " + acao + ".");
        }
        return proposta;
    }

    private Proposta buscarDoProfissional(Long usuarioId, Long id) {
        Profissional profissional = buscarProfissionalAtivo(usuarioId);
        return propostaRepository.findById(id)
                .filter(proposta -> proposta.getProfissional().getId().equals(profissional.getId()))
                .orElseThrow(PropostaNaoEncontradaException::new);
    }

}
