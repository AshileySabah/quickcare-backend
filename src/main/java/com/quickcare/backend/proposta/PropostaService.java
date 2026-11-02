package com.quickcare.backend.proposta;

import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.dto.PropostaRequest;
import com.quickcare.backend.proposta.dto.PropostaResponse;
import com.quickcare.backend.proposta.exception.PropostaJaEnviadaException;
import com.quickcare.backend.proposta.exception.PropostaNaoEncontradaException;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
import com.quickcare.backend.solicitacao.SolicitacaoRepository;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.SolicitacaoNaoEncontradaException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.validacao.StatusValidacao;
import com.quickcare.backend.validacao.exception.CadastroNaoValidadoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PropostaService {

    private final PropostaRepository propostaRepository;
    private final SolicitacaoRepository solicitacaoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;
    private final Notificador notificador;

    /**
     * O profissional só envia proposta para solicitação compatível com ele e
     * que ainda recebe propostas — nunca para a que ele mesmo abriu no perfil
     * de paciente. Depois de uma recusada ou cancelada, pode enviar outra. A primeira proposta move a solicitação de ABERTA para
     * RECEBENDO_PROPOSTAS.
     */
    @Transactional
    public PropostaResponse enviar(Long usuarioId, Long solicitacaoId, PropostaRequest request) {
        Profissional profissional = buscarProfissionalAtivo(usuarioId);
        if (profissional.getStatusValidacao() != StatusValidacao.APROVADO) {
            throw new CadastroNaoValidadoException("Só profissionais com cadastro aprovado podem enviar propostas.");
        }
        Solicitacao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .filter(s -> !s.ehDoUsuario(usuarioId))
                .filter(s -> s.atendidaPor(profissional))
                .orElseThrow(SolicitacaoNaoEncontradaException::new);

        if (!solicitacao.getStatus().recebePropostas()) {
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
        Solicitacao solicitacao = solicitacaoRepository.findById(solicitacaoId)
                .orElseThrow(SolicitacaoNaoEncontradaException::new);

        if (solicitacao.ehDoUsuario(usuarioId)) {
            return propostaRepository.findBySolicitacaoIdOrderByCriadoEmAsc(solicitacaoId).stream()
                    .filter(proposta -> proposta.getStatus() != StatusProposta.CANCELADA)
                    .map(this::responder)
                    .toList();
        }

        Profissional profissional = profissionalRepository.findByUsuarioId(usuarioId)
                .orElseThrow(SolicitacaoNaoEncontradaException::new);
        List<Proposta> minhas = propostaRepository
                .findBySolicitacaoIdAndProfissionalIdOrderByCriadoEmAsc(solicitacaoId, profissional.getId());

        if (minhas.isEmpty() && !solicitacao.atendidaPor(profissional)) {
            throw new SolicitacaoNaoEncontradaException();
        }

        return minhas.stream().map(this::responder).toList();
    }

    @Transactional(readOnly = true)
    public List<PropostaResponse> listarDoProfissional(Long usuarioId) {
        Profissional profissional = buscarProfissionalAtivo(usuarioId);

        return propostaRepository.findByProfissionalIdOrderByCriadoEmDesc(profissional.getId()).stream()
                .map(this::responder)
                .toList();
    }

    /** Visível para o profissional autor e para o paciente dono da solicitação. */
    @Transactional(readOnly = true)
    public PropostaResponse buscar(Long usuarioId, Long id) {
        Proposta proposta = propostaRepository.findById(id)
                .filter(p -> p.ehDoUsuario(usuarioId) || p.getSolicitacao().ehDoUsuario(usuarioId))
                .orElseThrow(PropostaNaoEncontradaException::new);

        return responder(proposta);
    }

    @Transactional
    public PropostaResponse atualizar(Long usuarioId, Long id, PropostaRequest request) {
        Proposta proposta = buscarAlteravelDoProfissional(usuarioId, id, "editada");
        boolean estavaAceita = proposta.getStatus() == StatusProposta.ACEITA;

        if (estavaAceita) {
            proposta.voltarParaReaceite(MotivoReaceite.PROPOSTA_ALTERADA);
            reabrirParaPropostas(proposta.getSolicitacao());
        }

        aplicarDados(proposta, request);
        Proposta salva = propostaRepository.saveAndFlush(proposta);
        notificador.propostaAlterada(salva, estavaAceita);
        return responder(salva);
    }

    /**
     * Exclusão lógica, também da proposta já aceita (a vaga reabre). Se não
     * sobrou nenhuma proposta pendente nem aceita, a solicitação volta a ABERTA.
     */
    @Transactional
    public PropostaResponse cancelar(Long usuarioId, Long id) {
        Proposta proposta = buscarAlteravelDoProfissional(usuarioId, id, "cancelada");
        boolean estavaAceita = proposta.getStatus() == StatusProposta.ACEITA;

        if (estavaAceita) {
            proposta.liberarVaga();
            reabrirParaPropostas(proposta.getSolicitacao());
        }

        proposta.setStatus(StatusProposta.CANCELADA);
        propostaRepository.saveAndFlush(proposta);
        reabrirSeNaoSobrouProposta(proposta.getSolicitacao());
        notificador.propostaCancelada(proposta, estavaAceita);

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
        reabrirSeNaoSobrouProposta(proposta.getSolicitacao());
        notificador.propostaRecusada(proposta, false);

        return responder(proposta);
    }

    /** Uma vaga reabriu: a solicitação em andamento volta a receber propostas. */
    private void reabrirParaPropostas(Solicitacao solicitacao) {
        if (solicitacao.getStatus() == StatusSolicitacao.EM_ANDAMENTO) {
            solicitacao.setStatus(StatusSolicitacao.RECEBENDO_PROPOSTAS);
        }
    }

    /** Sem proposta pendente nem vaga preenchida, a solicitação volta a ABERTA. */
    private void reabrirSeNaoSobrouProposta(Solicitacao solicitacao) {
        if (solicitacao.getStatus() == StatusSolicitacao.RECEBENDO_PROPOSTAS
                && !solicitacao.temVagaPreenchida()
                && !propostaRepository.existsBySolicitacaoIdAndStatus(solicitacao.getId(), StatusProposta.PENDENTE)) {
            solicitacao.setStatus(StatusSolicitacao.ABERTA);
        }
    }

    /**
     * O paciente aceita uma proposta, que ocupa uma vaga de uma linha que o
     * profissional atende. Pendentes que não cabem mais em nenhuma vaga são
     * recusadas; com todas as vagas preenchidas a solicitação entra em
     * andamento.
     */
    @Transactional
    public PropostaResponse aceitar(Long usuarioId, Long id) {
        Proposta proposta = buscarDoPaciente(usuarioId, id);
        Solicitacao solicitacao = proposta.getSolicitacao();

        if (!proposta.getStatus().pendente() || !solicitacao.getStatus().recebePropostas()) {
            throw new TransicaoStatusInvalidaException("Esta proposta não está mais disponível para aceite.");
        }

        SolicitacaoProfissional vaga = solicitacao.vagaDisponivelPara(proposta.getProfissional())
                .orElseThrow(() -> new TransicaoStatusInvalidaException(
                        "Não há mais vagas abertas para o perfil deste profissional."));
        vaga.preencherVaga();
        proposta.setVaga(vaga);
        proposta.setStatus(StatusProposta.ACEITA);
        proposta.setMotivoReaceite(null);

        boolean completa = solicitacao.todasAsVagasPreenchidas();
        propostaRepository.findBySolicitacaoIdAndStatus(solicitacao.getId(), StatusProposta.PENDENTE).stream()
                .filter(outra -> !outra.getId().equals(proposta.getId()))
                .filter(outra -> completa || solicitacao.vagaDisponivelPara(outra.getProfissional()).isEmpty())
                .forEach(outra -> {
                    outra.setStatus(StatusProposta.RECUSADA);
                    notificador.propostaRecusada(outra, true);
                });

        if (completa) {
            solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        }

        Proposta salva = propostaRepository.saveAndFlush(proposta);
        notificador.propostaAceita(salva);
        return responder(salva);
    }

    private PropostaResponse responder(Proposta proposta) {
        return PropostaResponse.de(proposta, armazenamentoDocumentoService::urlDoAvatar);
    }

    private void aplicarDados(Proposta proposta, PropostaRequest request) {
        proposta.setValor(request.valor());
        proposta.setAbordagem(request.abordagem().trim());
        proposta.setPrazo(request.prazo());
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
