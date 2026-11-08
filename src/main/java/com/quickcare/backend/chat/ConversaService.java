package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.ConversaResponse;
import com.quickcare.backend.chat.dto.ParticipanteResponse;
import com.quickcare.backend.chat.dto.UltimaMensagemResponse;
import com.quickcare.backend.chat.exception.ConversaAcessoNegadoException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Regras de quem pode conversar. A conversa pertence a uma solicitação e dela participam o
 * paciente dono da solicitação e os profissionais com proposta ACEITA nela. Nada é gravado: se a
 * proposta deixa de estar aceita, o profissional perde o acesso automaticamente.
 */
@Service
@RequiredArgsConstructor
public class ConversaService {

    static final StatusProposta STATUS_QUE_LIBERA_O_CHAT = StatusProposta.ACEITA;

    private static final String PAPEL_PACIENTE = "PACIENTE";
    private static final String PAPEL_PROFISSIONAL = "PROFISSIONAL";

    private static final Comparator<ConversaResponse> MAIS_RECENTE_PRIMEIRO = Comparator
            .<ConversaResponse, Instant>comparing(ConversaService::instanteDaUltimaMensagem).reversed()
            .thenComparing(ConversaResponse::solicitacaoId, Comparator.reverseOrder());

    private final ChatPropostaRepository chatPropostaRepository;
    private final UsuarioRepository usuarioRepository;
    private final MensagemRepository mensagemRepository;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;

    /**
     * Falha com {@link ConversaAcessoNegadoException} (403) se o usuário não participa da
     * conversa. Solicitações inexistentes também resultam em 403, para não revelar quais ids existem.
     */
    @Transactional(readOnly = true)
    public void exigirParticipante(Long solicitacaoId, Long usuarioId) {
        if (solicitacaoId == null || usuarioId == null
                || !chatPropostaRepository.usuarioParticipa(solicitacaoId, usuarioId, STATUS_QUE_LIBERA_O_CHAT)) {
            throw new ConversaAcessoNegadoException();
        }
    }

    /** Ids dos usuários que participam da conversa, com o paciente primeiro. */
    @Transactional(readOnly = true)
    public List<Long> participantes(Long solicitacaoId) {
        return participantesDe(solicitacaoId).todos();
    }

    /** Conversas do usuário, da que recebeu mensagem mais recentemente para a mais antiga. */
    @Transactional(readOnly = true)
    public List<ConversaResponse> listar(Long usuarioId) {
        List<Long> solicitacaoIds = chatPropostaRepository
                .solicitacaoIdsComChat(usuarioId, STATUS_QUE_LIBERA_O_CHAT);
        if (solicitacaoIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Participantes> porSolicitacao = new LinkedHashMap<>();
        Set<Long> todosOsUsuarios = new LinkedHashSet<>();
        for (Long solicitacaoId : solicitacaoIds) {
            Participantes participantes = participantesDe(solicitacaoId);
            porSolicitacao.put(solicitacaoId, participantes);
            todosOsUsuarios.addAll(participantes.todos());
        }

        Map<Long, Usuario> usuarios = usuarioRepository.findAllById(todosOsUsuarios).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        Map<Long, Mensagem> ultimas = mensagemRepository.findUltimasPorSolicitacao(solicitacaoIds).stream()
                .collect(Collectors.toMap(Mensagem::getSolicitacaoId, Function.identity(), (a, b) -> a));
        Map<Long, String> avatares = new HashMap<>();

        return solicitacaoIds.stream()
                .map(id -> paraResponse(id, porSolicitacao.get(id), usuarios, avatares, ultimas.get(id)))
                .sorted(MAIS_RECENTE_PRIMEIRO)
                .toList();
    }

    private Participantes participantesDe(Long solicitacaoId) {
        List<Long> paciente = chatPropostaRepository.usuarioIdsDoPaciente(solicitacaoId, STATUS_QUE_LIBERA_O_CHAT);
        List<Long> profissionais = chatPropostaRepository
                .usuarioIdsDosProfissionais(solicitacaoId, STATUS_QUE_LIBERA_O_CHAT);
        return new Participantes(paciente.isEmpty() ? null : paciente.get(0), profissionais);
    }

    private ConversaResponse paraResponse(
            Long solicitacaoId,
            Participantes participantes,
            Map<Long, Usuario> usuarios,
            Map<Long, String> avatares,
            Mensagem ultima) {
        List<ParticipanteResponse> lista = new ArrayList<>();
        if (participantes.pacienteId() != null) {
            lista.add(participante(participantes.pacienteId(), PAPEL_PACIENTE, usuarios, avatares));
        }
        participantes.profissionalIds()
                .forEach(id -> lista.add(participante(id, PAPEL_PROFISSIONAL, usuarios, avatares)));
        return new ConversaResponse(solicitacaoId, lista, ultima == null ? null : UltimaMensagemResponse.de(ultima));
    }

    private ParticipanteResponse participante(
            Long usuarioId, String papel, Map<Long, Usuario> usuarios, Map<Long, String> avatares) {
        Usuario usuario = usuarios.get(usuarioId);
        String nome = usuario != null ? usuario.getNome() : "Usuário #" + usuarioId;
        return new ParticipanteResponse(usuarioId, nome, papel, avatarDe(usuarioId, usuario, avatares));
    }

    /** A URL é assinada no storage; é gerada uma vez por usuário em cada listagem. */
    private String avatarDe(Long usuarioId, Usuario usuario, Map<Long, String> avatares) {
        if (avatares.containsKey(usuarioId)) {
            return avatares.get(usuarioId);
        }
        String url = usuario == null ? null : armazenamentoDocumentoService.urlDoAvatar(usuario);
        avatares.put(usuarioId, url);
        return url;
    }

    private static Instant instanteDaUltimaMensagem(ConversaResponse conversa) {
        UltimaMensagemResponse ultima = conversa.ultimaMensagem();
        return ultima == null || ultima.dataEnvio() == null ? Instant.MIN : ultima.dataEnvio();
    }

    private record Participantes(Long pacienteId, List<Long> profissionalIds) {

        List<Long> todos() {
            Set<Long> ids = new LinkedHashSet<>();
            if (pacienteId != null) {
                ids.add(pacienteId);
            }
            ids.addAll(profissionalIds);
            return List.copyOf(ids);
        }
    }

}
