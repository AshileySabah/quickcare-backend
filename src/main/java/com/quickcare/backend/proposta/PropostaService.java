package com.quickcare.backend.proposta;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
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
import com.quickcare.backend.validacao.exception.CadastroNaoValidadoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PropostaService {

    /** Um atendimento proposto não passa de um ano. */
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
    private final Clock clock;

    /**
     * O profissional só envia proposta para solicitação compatível com ele e
     * que ainda recebe propostas — nunca para a que ele mesmo abriu no perfil
     * de paciente. Depois de uma recusada ou cancelada, pode enviar outra. A
     * primeira proposta move a solicitação de ABERTA para RECEBENDO_PROPOSTAS.
     */
    @Transactional
    public PropostaResponse enviar(Long usuarioId, Long solicitacaoId, PropostaRequest request) {
        Profissional profissional = buscarProfissionalAtivo(usuarioId);
        if (profissional.getStatusValidacao() != StatusValidacao.APROVADO) {
            throw new CadastroNaoValidadoException("Só profissionais com cadastro aprovado podem enviar propostas.");
        }
        ocorrenciaService.exigirSemBloqueio(usuarioId, TipoPerfil.PROFISSIONAL);
        Solicitacao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .filter(s -> !s.ehDoUsuario(usuarioId))
                .filter(s -> s.atendidaPor(profissional))
                .orElseThrow(SolicitacaoNaoEncontradaException::new);

        // Bloqueados entre si: a mesma resposta de quando fecha, sem revelar o bloqueio.
        if (!solicitacao.getStatus().recebePropostas() || bloqueados(solicitacao, profissional)) {
            throw new TransicaoStatusInvalidaException("Esta solicitação não está mais recebendo propostas.");
        }
        if (propostaRepository.existsBySolicitacaoIdAndProfissionalIdAndStatusIn(
                solicitacaoId, profissional.getId(), StatusProposta.ATIVAS)) {
            throw new PropostaJaEnviadaException();
        }

        Proposta proposta = Proposta.builder()
                .solicitacao(solicitacao)
                .profissional(profissional)
                .status(StatusProposta.PENDENTE)
                .build();
        aplicarDados(proposta, request);

        if (solicitacao.getStatus() == StatusSolicitacao.ABERTA) {
            solicitacao.setStatus(StatusSolicitacao.RECEBENDO_PROPOSTAS);
        }

        Proposta salva = propostaRepository.save(proposta);
        notificador.propostaRecebida(salva);
        return responder(salva);
    }

    /**
     * O paciente dono vê todas as propostas não canceladas; o profissional vê
     * só as suas. Para os demais a solicitação "não existe".
     */
    @Transactional(readOnly = true)
    public List<PropostaResponse> listarDaSolicitacao(Long usuarioId, Long solicitacaoId) {
        atendimentoService.processarVencidos();
        Solicitacao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(SolicitacaoNaoEncontradaException::new);

        if (solicitacao.ehDoUsuario(usuarioId)) {
            return responder(propostaRepository.findBySolicitacaoIdOrderByCriadoEmAsc(solicitacaoId).stream()
                    .filter(proposta -> proposta.getStatus() != StatusProposta.CANCELADA)
                    .toList());
        }

        Profissional profissional = profissionalRepository.findByUsuarioId(usuarioId)
                .orElseThrow(SolicitacaoNaoEncontradaException::new);
        List<Proposta> minhas = propostaRepository
                .findBySolicitacaoIdAndProfissionalIdOrderByCriadoEmAsc(solicitacaoId, profissional.getId());

        if (minhas.isEmpty() && !solicitacao.atendidaPor(profissional)) {
            throw new SolicitacaoNaoEncontradaException();
        }

        return responder(minhas);
    }

    @Transactional(readOnly = true)
    public List<PropostaResponse> listarDoProfissional(Long usuarioId) {
        atendimentoService.processarVencidos();
        Profissional profissional = buscarProfissionalAtivo(usuarioId);
        return responder(propostaRepository.findByProfissionalIdOrderByCriadoEmDesc(profissional.getId()));
    }

    /** Visível para o profissional autor e para o paciente dono da solicitação. */
    @Transactional(readOnly = true)
    public PropostaResponse buscar(Long usuarioId, Long id) {
        atendimentoService.processarVencidos();
        Proposta proposta = propostaRepository.findById(id)
                .filter(p -> p.ehDoUsuario(usuarioId) || p.getSolicitacao().ehDoUsuario(usuarioId))
                .orElseThrow(PropostaNaoEncontradaException::new);

        return responder(proposta);
    }

    /**
     * Editar a proposta já aceita a tira do aceite: o atendimento cai, a vaga
     * reabre e o paciente aceita de novo (ou escolhe outra).
     */
    @Transactional
    public PropostaResponse atualizar(Long usuarioId, Long id, PropostaRequest request) {
        Proposta proposta = buscarAlteravelDoProfissional(usuarioId, id, "editada");
        boolean estavaAceita = proposta.getStatus() == StatusProposta.ACEITA;

        if (estavaAceita) {
            atendimentoService.cancelarPorAlteracaoDaProposta(proposta);
            proposta.voltarParaReaceite(MotivoReaceite.PROPOSTA_ALTERADA);
        }

        aplicarDados(proposta, request);
        Proposta salva = propostaRepository.saveAndFlush(proposta);
        recalcularSolicitacao(salva.getSolicitacao());
        notificador.propostaAlterada(salva, estavaAceita);
        return responder(salva);
    }

    /**
     * Exclusão lógica. A aceita é o mesmo que cancelar o atendimento (a vaga
     * reabre e a regra das 48h decide se conta ocorrência).
     */
    @Transactional
    public PropostaResponse cancelar(Long usuarioId, Long id) {
        Proposta proposta = buscarAlteravelDoProfissional(usuarioId, id, "cancelada");

        if (proposta.getStatus() == StatusProposta.ACEITA) {
            atendimentoService.cancelarPeloProfissional(proposta);
        } else {
            proposta.setStatus(StatusProposta.CANCELADA);
            propostaRepository.saveAndFlush(proposta);
            recalcularSolicitacao(proposta.getSolicitacao());
            notificador.propostaCancelada(proposta, false);
        }

        return responder(proposta);
    }

    /**
     * O paciente recusa uma proposta pendente; para o profissional ela aparece
     * como recusada (diferente de cancelada, que é quando ele mesmo desiste).
     */
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

        return responder(proposta);
    }

    /**
     * O paciente aceita uma proposta: ela ocupa uma vaga (livre, ou a reservada
     * de quem ainda não reaceitou) e vira um atendimento agendado.
     */
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
        return responder(salva);
    }

    private boolean bloqueados(Solicitacao solicitacao, Profissional profissional) {
        return bloqueioUsuarioRepository.existsByPacienteIdAndProfissionalId(
                solicitacao.getPaciente().getId(), profissional.getId());
    }

    private void recalcularSolicitacao(Solicitacao solicitacao) {
        solicitacao.recalcularStatus(
                propostaRepository.existsBySolicitacaoIdAndStatus(solicitacao.getId(), StatusProposta.PENDENTE));
    }

    private PropostaResponse responder(Proposta proposta) {
        return responder(List.of(proposta)).get(0);
    }

    /** Uma consulta para os atendimentos da lista toda; de cada proposta, o mais recente. */
    private List<PropostaResponse> responder(List<Proposta> propostas) {
        if (propostas.isEmpty()) {
            return List.of();
        }
        Map<Long, Atendimento> atendimentoPorProposta = atendimentoRepository
                .findByPropostaIdInOrderByIdAsc(propostas.stream().map(Proposta::getId).toList()).stream()
                .collect(Collectors.toMap(atendimento -> atendimento.getProposta().getId(), Function.identity(),
                        (anterior, maisRecente) -> maisRecente));
        Instant agora = Instant.now(clock);

        return propostas.stream()
                .map(proposta -> PropostaResponse.de(proposta, atendimentoPorProposta.get(proposta.getId()), agora,
                        armazenamentoDocumentoService::urlDoAvatar))
                .toList();
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

    /** Proposta recebida numa solicitação do paciente, que precisa estar ativo. */
    private Proposta buscarDoPaciente(Long usuarioId, Long id) {
        Proposta proposta = propostaRepository.findById(id)
                .filter(p -> p.getSolicitacao().ehDoUsuario(usuarioId))
                .orElseThrow(PropostaNaoEncontradaException::new);

        if (proposta.getSolicitacao().getPaciente().isBanido()) {
            throw new PerfilNaoAutorizadoException("Apenas pacientes podem responder propostas.");
        }
        return proposta;
    }

    /** Pendente ou aceita, numa solicitação ainda em curso. */
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
