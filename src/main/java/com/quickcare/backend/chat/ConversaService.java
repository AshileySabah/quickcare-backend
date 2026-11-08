package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.ConversaResponse;
import com.quickcare.backend.chat.dto.ParticipanteResponse;
import com.quickcare.backend.chat.exception.ConversaAcessoNegadoException;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final ChatPropostaRepository chatPropostaRepository;
    private final UsuarioRepository usuarioRepository;

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

    @Transactional(readOnly = true)
    public List<ConversaResponse> listar(Long usuarioId) {
        List<Long> solicitacaoIds = chatPropostaRepository
                .solicitacaoIdsComChat(usuarioId, STATUS_QUE_LIBERA_O_CHAT).stream()
                .sorted(Comparator.reverseOrder())
                .toList();
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

        Map<Long, String> nomes = usuarioRepository.findAllById(todosOsUsuarios).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getNome));

        return porSolicitacao.entrySet().stream()
                .map(entry -> paraResponse(entry.getKey(), entry.getValue(), nomes))
                .toList();
    }

    private Participantes participantesDe(Long solicitacaoId) {
        List<Long> paciente = chatPropostaRepository.usuarioIdsDoPaciente(solicitacaoId, STATUS_QUE_LIBERA_O_CHAT);
        List<Long> profissionais = chatPropostaRepository
                .usuarioIdsDosProfissionais(solicitacaoId, STATUS_QUE_LIBERA_O_CHAT);
        return new Participantes(paciente.isEmpty() ? null : paciente.get(0), profissionais);
    }

    private ConversaResponse paraResponse(Long solicitacaoId, Participantes participantes, Map<Long, String> nomes) {
        List<ParticipanteResponse> lista = new ArrayList<>();
        if (participantes.pacienteId() != null) {
            lista.add(participante(participantes.pacienteId(), PAPEL_PACIENTE, nomes));
        }
        participantes.profissionalIds()
                .forEach(id -> lista.add(participante(id, PAPEL_PROFISSIONAL, nomes)));
        return new ConversaResponse(solicitacaoId, lista);
    }

    private ParticipanteResponse participante(Long usuarioId, String papel, Map<Long, String> nomes) {
        return new ParticipanteResponse(usuarioId, nomes.getOrDefault(usuarioId, "Usuário #" + usuarioId), papel);
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
