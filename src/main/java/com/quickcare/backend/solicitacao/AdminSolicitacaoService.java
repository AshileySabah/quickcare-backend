package com.quickcare.backend.solicitacao;

import com.quickcare.backend.administrador.AcaoAdmin;
import com.quickcare.backend.administrador.AcaoAdminRepository;
import com.quickcare.backend.administrador.AcessoAdmin;
import com.quickcare.backend.administrador.TipoAcaoAdmin;
import com.quickcare.backend.administrador.dto.AcaoAdminResponse;
import com.quickcare.backend.administrador.dto.ParteResponse;
import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.atendimento.dto.AtendimentoResponse;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.dto.SolicitacaoAdminDetalheResponse;
import com.quickcare.backend.solicitacao.dto.SolicitacaoAdminResumoResponse;
import com.quickcare.backend.solicitacao.dto.SolicitacaoResponse;
import com.quickcare.backend.solicitacao.exception.SolicitacaoNaoEncontradaException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminSolicitacaoService {

    private final SolicitacaoRepository solicitacaoRepository;
    private final PropostaRepository propostaRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final AtendimentoService atendimentoService;
    private final AcaoAdminRepository acaoAdminRepository;
    private final AcessoAdmin acessoAdmin;
    private final Notificador notificador;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SolicitacaoAdminResumoResponse> listar(Long adminUsuarioId, Collection<StatusSolicitacao> status, String busca) {
        acessoAdmin.exigir(adminUsuarioId);
        String termo = normalizar(busca);
        return solicitacaoRepository.findAllByOrderByCriadoEmDesc().stream()
                .filter(solicitacao -> status == null || status.isEmpty() || status.contains(solicitacao.getStatus()))
                .filter(solicitacao -> termo.isEmpty()
                        || solicitacao.getId().toString().equals(termo.replace("#", ""))
                        || normalizar(solicitacao.getPaciente().getUsuario().getNome()).contains(termo)
                        || normalizar(solicitacao.getPaciente().getUsuario().getEmail()).contains(termo))
                .map(SolicitacaoAdminResumoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public SolicitacaoAdminDetalheResponse detalhar(Long adminUsuarioId, Long id) {
        acessoAdmin.exigir(adminUsuarioId);
        return detalhe(buscar(id));
    }

    @Transactional
    public SolicitacaoAdminDetalheResponse alterarStatus(Long adminUsuarioId, Long id, AcaoAdminSolicitacao acao,
                                                         String justificativa) {
        Usuario admin = acessoAdmin.exigir(adminUsuarioId);
        Solicitacao solicitacao = buscar(id);
        if (!acoesPermitidas(solicitacao.getStatus()).contains(acao)) {
            throw new TransicaoStatusInvalidaException("Não é possível deixar a solicitação " + acao.getParticipio()
                    + " a partir do status atual.");
        }

        StatusSolicitacao anterior = solicitacao.getStatus();
        List<Proposta> afetadas = switch (acao) {
            case REABRIR -> {
                atendimentoService.reabrirPeloAdmin(solicitacao);
                yield List.of();
            }
            case CONCLUIR -> atendimentoService.encerrarPeloAdmin(solicitacao, StatusSolicitacao.CONCLUIDA);
            case CANCELAR -> atendimentoService.encerrarPeloAdmin(solicitacao, StatusSolicitacao.CANCELADA);
        };

        acessoAdmin.registrar(admin, tipoDaAcao(acao), AcaoAdmin.Alvo.SOLICITACAO, solicitacao.getId(),
                anterior + " → " + solicitacao.getStatus(), justificativa);
        notificador.solicitacaoAlteradaPeloAdmin(solicitacao, acao, afetadas, justificativa);
        return detalhe(solicitacao);
    }

    static List<AcaoAdminSolicitacao> acoesPermitidas(StatusSolicitacao status) {
        return Stream.of(AcaoAdminSolicitacao.values())
                .filter(acao -> switch (acao) {
                    case REABRIR -> !StatusSolicitacao.EM_CURSO.contains(status);
                    case CONCLUIR -> status != StatusSolicitacao.CONCLUIDA;
                    case CANCELAR -> status != StatusSolicitacao.CANCELADA;
                })
                .toList();
    }

    private static TipoAcaoAdmin tipoDaAcao(AcaoAdminSolicitacao acao) {
        return switch (acao) {
            case REABRIR -> TipoAcaoAdmin.SOLICITACAO_REABERTA;
            case CONCLUIR -> TipoAcaoAdmin.SOLICITACAO_CONCLUIDA;
            case CANCELAR -> TipoAcaoAdmin.SOLICITACAO_CANCELADA;
        };
    }

    private Solicitacao buscar(Long id) {
        return solicitacaoRepository.findById(id).orElseThrow(SolicitacaoNaoEncontradaException::new);
    }

    private SolicitacaoAdminDetalheResponse detalhe(Solicitacao solicitacao) {
        Instant agora = Instant.now(clock);
        List<Proposta> propostas = propostaRepository.findBySolicitacaoIdOrderByCriadoEmAsc(solicitacao.getId());
        Map<Long, Atendimento> atendimentos = propostas.isEmpty() ? Map.of()
                : atendimentoRepository.findByPropostaIdInOrderByIdAsc(propostas.stream().map(Proposta::getId).toList())
                        .stream().collect(Collectors.toMap(atendimento -> atendimento.getProposta().getId(),
                                Function.identity(), (antigo, novo) -> novo));
        List<Atendimento> ativos = atendimentos.values().stream()
                .filter(atendimento -> atendimento.getStatus().ativo())
                .toList();
        long totalPropostas = propostas.stream().filter(proposta -> proposta.getStatus() != StatusProposta.CANCELADA).count();

        return new SolicitacaoAdminDetalheResponse(
                SolicitacaoAdminResumoResponse.de(solicitacao),
                SolicitacaoResponse.de(solicitacao, totalPropostas, ativos),
                propostas.stream()
                        .map(proposta -> new SolicitacaoAdminDetalheResponse.PropostaAdmin(
                                proposta.getId(),
                                ParteResponse.de(proposta.getProfissional().getUsuario()),
                                proposta.getStatus(),
                                proposta.getValor(),
                                proposta.getInicio(),
                                proposta.getTermino(),
                                atendimentos.containsKey(proposta.getId())
                                        ? AtendimentoResponse.de(atendimentos.get(proposta.getId()), agora) : null))
                        .toList(),
                acoesPermitidas(solicitacao.getStatus()),
                acaoAdminRepository.findByAlvoTipoAndAlvoIdOrderByCriadoEmDesc(AcaoAdmin.Alvo.SOLICITACAO, solicitacao.getId())
                        .stream().map(AcaoAdminResponse::de).toList());
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

}
