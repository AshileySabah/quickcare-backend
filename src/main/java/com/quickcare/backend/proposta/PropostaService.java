package com.quickcare.backend.proposta;

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

    /**
     * O profissional só envia proposta para solicitação compatível com ele e
     * que ainda recebe propostas — nunca para a que ele mesmo abriu no perfil
     * de paciente. A primeira proposta move a solicitação de ABERTA para
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
        if (propostaRepository.existsBySolicitacaoIdAndProfissionalIdAndStatusNot(
                solicitacaoId, profissional.getId(), StatusProposta.CANCELADA)) {
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

        return PropostaResponse.de(propostaRepository.save(proposta));
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
                    .map(PropostaResponse::de)
                    .toList();
        }

        Profissional profissional = profissionalRepository.findByUsuarioId(usuarioId)
                .orElseThrow(SolicitacaoNaoEncontradaException::new);
        List<Proposta> minhas = propostaRepository
                .findBySolicitacaoIdAndProfissionalIdOrderByCriadoEmAsc(solicitacaoId, profissional.getId());

        if (minhas.isEmpty() && !solicitacao.atendidaPor(profissional)) {
            throw new SolicitacaoNaoEncontradaException();
        }

        return minhas.stream().map(PropostaResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<PropostaResponse> listarDoProfissional(Long usuarioId) {
        Profissional profissional = buscarProfissionalAtivo(usuarioId);

        return propostaRepository.findByProfissionalIdOrderByCriadoEmDesc(profissional.getId()).stream()
                .map(PropostaResponse::de)
                .toList();
    }

    /** Visível para o profissional autor e para o paciente dono da solicitação. */
    @Transactional(readOnly = true)
    public PropostaResponse buscar(Long usuarioId, Long id) {
        Proposta proposta = propostaRepository.findById(id)
                .filter(p -> p.ehDoUsuario(usuarioId) || p.getSolicitacao().ehDoUsuario(usuarioId))
                .orElseThrow(PropostaNaoEncontradaException::new);

        return PropostaResponse.de(proposta);
    }

    @Transactional
    public PropostaResponse atualizar(Long usuarioId, Long id, PropostaRequest request) {
        Proposta proposta = buscarDoProfissional(usuarioId, id);

        if (!proposta.getStatus().pendente()) {
            throw new TransicaoStatusInvalidaException("Esta proposta não pode mais ser editada.");
        }
        if (!proposta.getSolicitacao().getStatus().recebePropostas()) {
            throw new TransicaoStatusInvalidaException("Esta solicitação não está mais recebendo propostas.");
        }

        aplicarDados(proposta, request);
        return PropostaResponse.de(propostaRepository.saveAndFlush(proposta));
    }

    /**
     * Exclusão lógica. Se não sobrou nenhuma proposta pendente nem aceita, a
     * solicitação volta a ABERTA.
     */
    @Transactional
    public PropostaResponse cancelar(Long usuarioId, Long id) {
        Proposta proposta = buscarDoProfissional(usuarioId, id);

        if (!proposta.getStatus().pendente()) {
            throw new TransicaoStatusInvalidaException("Esta proposta não pode mais ser cancelada.");
        }

        proposta.setStatus(StatusProposta.CANCELADA);
        propostaRepository.saveAndFlush(proposta);

        Solicitacao solicitacao = proposta.getSolicitacao();
        if (solicitacao.getStatus() == StatusSolicitacao.RECEBENDO_PROPOSTAS
                && !solicitacao.temVagaPreenchida()
                && !propostaRepository.existsBySolicitacaoIdAndStatus(solicitacao.getId(), StatusProposta.PENDENTE)) {
            solicitacao.setStatus(StatusSolicitacao.ABERTA);
        }

        return PropostaResponse.de(proposta);
    }

    /**
     * O paciente aceita uma proposta, que ocupa uma vaga de uma linha que o
     * profissional atende. Pendentes que não cabem mais em nenhuma vaga são
     * recusadas; com todas as vagas preenchidas a solicitação entra em
     * andamento.
     */
    @Transactional
    public PropostaResponse aceitar(Long usuarioId, Long id) {
        Proposta proposta = propostaRepository.findById(id)
                .filter(p -> p.getSolicitacao().ehDoUsuario(usuarioId))
                .orElseThrow(PropostaNaoEncontradaException::new);
        Solicitacao solicitacao = proposta.getSolicitacao();

        if (solicitacao.getPaciente().isBanido()) {
            throw new PerfilNaoAutorizadoException("Apenas pacientes podem aceitar propostas.");
        }
        if (!proposta.getStatus().pendente() || !solicitacao.getStatus().recebePropostas()) {
            throw new TransicaoStatusInvalidaException("Esta proposta não está mais disponível para aceite.");
        }

        SolicitacaoProfissional vaga = solicitacao.vagaDisponivelPara(proposta.getProfissional())
                .orElseThrow(() -> new TransicaoStatusInvalidaException(
                        "Não há mais vagas abertas para o perfil deste profissional."));
        vaga.preencherVaga();
        proposta.setVaga(vaga);
        proposta.setStatus(StatusProposta.ACEITA);

        boolean completa = solicitacao.todasAsVagasPreenchidas();
        propostaRepository.findBySolicitacaoIdAndStatus(solicitacao.getId(), StatusProposta.PENDENTE).stream()
                .filter(outra -> !outra.getId().equals(proposta.getId()))
                .filter(outra -> completa || solicitacao.vagaDisponivelPara(outra.getProfissional()).isEmpty())
                .forEach(outra -> outra.setStatus(StatusProposta.RECUSADA));

        if (completa) {
            solicitacao.setStatus(StatusSolicitacao.EM_ANDAMENTO);
        }

        return PropostaResponse.de(propostaRepository.saveAndFlush(proposta));
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

    private Proposta buscarDoProfissional(Long usuarioId, Long id) {
        Profissional profissional = buscarProfissionalAtivo(usuarioId);
        return propostaRepository.findById(id)
                .filter(proposta -> proposta.getProfissional().getId().equals(profissional.getId()))
                .orElseThrow(PropostaNaoEncontradaException::new);
    }

}
