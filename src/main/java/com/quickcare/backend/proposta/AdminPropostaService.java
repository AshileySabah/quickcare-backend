package com.quickcare.backend.proposta;

import com.quickcare.backend.administrador.AcaoAdmin;
import com.quickcare.backend.administrador.AcessoAdmin;
import com.quickcare.backend.administrador.TipoAcaoAdmin;
import com.quickcare.backend.administrador.dto.ParteResponse;
import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.bloqueiousuario.BloqueioUsuarioRepository;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.proposta.dto.PropostaAdminResponse;
import com.quickcare.backend.proposta.exception.PropostaNaoEncontradaException;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminPropostaService {

    private final PropostaRepository propostaRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final AtendimentoService atendimentoService;
    private final BloqueioUsuarioRepository bloqueioUsuarioRepository;
    private final AcessoAdmin acessoAdmin;
    private final Notificador notificador;

    @Transactional(readOnly = true)
    public List<PropostaAdminResponse> listar(Long adminUsuarioId, Collection<StatusProposta> status, String busca) {
        acessoAdmin.exigir(adminUsuarioId);
        atendimentoService.processarVencidos();
        String termo = normalizar(busca);
        String numero = termo.replace("#", "");
        List<Proposta> propostas = propostaRepository.findAllByOrderByCriadoEmDesc().stream()
                .filter(proposta -> status == null || status.isEmpty() || status.contains(proposta.getStatus()))
                .filter(proposta -> termo.isEmpty()
                        || proposta.getId().toString().equals(numero)
                        || proposta.getSolicitacao().getId().toString().equals(numero)
                        || Stream.of(proposta.getProfissional().getUsuario(), proposta.getSolicitacao().getPaciente().getUsuario())
                                .anyMatch(usuario -> normalizar(usuario.getNome()).contains(termo)
                                        || normalizar(usuario.getEmail()).contains(termo)))
                .toList();
        return responder(propostas);
    }

    @Transactional
    public PropostaAdminResponse alterarStatus(Long adminUsuarioId, Long id, AcaoAdminProposta acao, String justificativa) {
        Usuario admin = acessoAdmin.exigir(adminUsuarioId);
        Proposta proposta = propostaRepository.findById(id).orElseThrow(PropostaNaoEncontradaException::new);
        if (!acoesPermitidas(proposta, atendimentoAtivo(proposta)).contains(acao)) {
            throw new TransicaoStatusInvalidaException("Não é possível deixar a proposta " + acao.getParticipio()
                    + " a partir do status atual.");
        }

        StatusProposta anterior = proposta.getStatus();
        switch (acao) {
            case RECUSAR -> proposta.setStatus(StatusProposta.RECUSADA);
            case CANCELAR -> atendimentoService.cancelarPeloAdmin(proposta);
            case REATIVAR -> reativar(proposta);
        }
        propostaRepository.saveAndFlush(proposta);
        atendimentoService.recalcularDepoisDoAdmin(proposta.getSolicitacao());

        acessoAdmin.registrar(admin, tipoDaAcao(acao), AcaoAdmin.Alvo.PROPOSTA, proposta.getId(),
                anterior + " → " + proposta.getStatus(), justificativa);
        notificador.propostaAlteradaPeloAdmin(proposta, acao, justificativa);
        return responder(List.of(proposta)).get(0);
    }

    private List<AcaoAdminProposta> acoesPermitidas(Proposta proposta, boolean atendimentoAtivo) {
        return Stream.of(AcaoAdminProposta.values())
                .filter(acao -> switch (acao) {
                    case RECUSAR -> proposta.getStatus() == StatusProposta.PENDENTE;
                    case CANCELAR -> proposta.getStatus() == StatusProposta.ACEITA && atendimentoAtivo;
                    case REATIVAR -> (proposta.getStatus() == StatusProposta.RECUSADA
                            || proposta.getStatus() == StatusProposta.CANCELADA)
                            && proposta.getSolicitacao().getStatus().recebePropostas();
                })
                .toList();
    }

    private void reativar(Proposta proposta) {
        Solicitacao solicitacao = proposta.getSolicitacao();
        if (propostaRepository.existsBySolicitacaoIdAndProfissionalIdAndStatusIn(
                solicitacao.getId(), proposta.getProfissional().getId(), StatusProposta.ATIVAS)) {
            throw new TransicaoStatusInvalidaException("O profissional já tem outra proposta valendo nesta solicitação.");
        }
        if (bloqueioUsuarioRepository.existsByPacienteIdAndProfissionalId(
                solicitacao.getPaciente().getId(), proposta.getProfissional().getId())) {
            throw new TransicaoStatusInvalidaException(
                    "O paciente e o profissional estão bloqueados entre si. Remova o bloqueio antes de reativar.");
        }
        proposta.setStatus(StatusProposta.PENDENTE);
        proposta.setMotivoReaceite(null);
    }

    private boolean atendimentoAtivo(Proposta proposta) {
        return atendimentoService.ativoDa(proposta).isPresent();
    }

    private List<PropostaAdminResponse> responder(List<Proposta> propostas) {
        if (propostas.isEmpty()) {
            return List.of();
        }
        Map<Long, Atendimento> atendimentos = atendimentoRepository
                .findByPropostaIdInOrderByIdAsc(propostas.stream().map(Proposta::getId).toList()).stream()
                .collect(Collectors.toMap(atendimento -> atendimento.getProposta().getId(), Function.identity(),
                        (antigo, novo) -> novo));
        return propostas.stream()
                .map(proposta -> {
                    Atendimento atendimento = atendimentos.get(proposta.getId());
                    Solicitacao solicitacao = proposta.getSolicitacao();
                    return new PropostaAdminResponse(
                            proposta.getId(),
                            proposta.getStatus(),
                            solicitacao.getId(),
                            solicitacao.getStatus(),
                            Notificador.servico(proposta),
                            ParteResponse.de(solicitacao.getPaciente().getUsuario()),
                            ParteResponse.de(proposta.getProfissional().getUsuario()),
                            proposta.getValor(),
                            proposta.getInicio(),
                            proposta.getTermino(),
                            atendimento == null ? null : atendimento.getStatus(),
                            acoesPermitidas(proposta, atendimento != null && atendimento.getStatus().ativo()),
                            proposta.getCriadoEm());
                })
                .toList();
    }

    private static TipoAcaoAdmin tipoDaAcao(AcaoAdminProposta acao) {
        return switch (acao) {
            case RECUSAR -> TipoAcaoAdmin.PROPOSTA_RECUSADA;
            case CANCELAR -> TipoAcaoAdmin.PROPOSTA_CANCELADA;
            case REATIVAR -> TipoAcaoAdmin.PROPOSTA_REATIVADA;
        };
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
