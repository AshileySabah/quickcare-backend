package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.ConversaResponse;
import com.quickcare.backend.chat.dto.ParticipanteResponse;
import com.quickcare.backend.chat.dto.UltimaMensagemResponse;
import com.quickcare.backend.chat.exception.ConversaAcessoNegadoException;
import com.quickcare.backend.chat.exception.ConversaSomenteLeituraException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Regras de quem conversa com quem. Cada solicitação tem canais:
 * <ul>
 *   <li><b>individual</b>: o paciente e um profissional (um canal por profissional);</li>
 *   <li><b>grupo</b>: o paciente e todos os profissionais, quando há dois ou mais.</li>
 * </ul>
 * Entra na conversa o profissional com proposta ACEITA. Quando a proposta deixa de estar aceita
 * ele sai: continua vendo o histórico escrito até a saída, mas não escreve mais. A participação é
 * sincronizada com as propostas a cada acesso, antes de qualquer leitura ou envio.
 */
@Service
@RequiredArgsConstructor
public class ConversaService {

    static final StatusProposta STATUS_QUE_LIBERA_O_CHAT = StatusProposta.ACEITA;

    private static final String PAPEL_PACIENTE = "PACIENTE";
    private static final String PAPEL_PROFISSIONAL = "PROFISSIONAL";
    private static final String TIPO_GRUPO = "GRUPO";
    private static final String TIPO_INDIVIDUAL = "INDIVIDUAL";

    private static final Comparator<ConversaResponse> MAIS_RECENTE_PRIMEIRO = Comparator
            .<ConversaResponse, Instant>comparing(ConversaService::instanteDaUltimaMensagem).reversed()
            .thenComparing(ConversaResponse::solicitacaoId, Comparator.reverseOrder());

    private final ChatPropostaRepository chatPropostaRepository;
    private final ConversaProfissionalRepository conversaProfissionalRepository;
    private final UsuarioRepository usuarioRepository;
    private final MensagemRepository mensagemRepository;
    private final ArmazenamentoDocumentoService armazenamentoDocumentoService;

    /**
     * O que o usuário pode fazer no canal: {@code profissionalId} nulo é o grupo; preenchido é a
     * conversa individual com esse profissional. Falha com {@link ConversaAcessoNegadoException}
     * (403) se o usuário não participa do canal, ou se ele não existe.
     */
    @Transactional
    public AcessoAoCanal acessoDe(Long solicitacaoId, Long profissionalId, Long usuarioId) {
        if (solicitacaoId == null || usuarioId == null) {
            throw new ConversaAcessoNegadoException();
        }
        Long pacienteId = chatPropostaRepository.usuarioIdDoPaciente(solicitacaoId).orElse(null);
        List<ConversaProfissional> profissionais = sincronizar(solicitacaoId);
        return calcularAcesso(pacienteId, profissionais, profissionalId, usuarioId)
                .orElseThrow(ConversaAcessoNegadoException::new);
    }

    /** Falha com 403 se o usuário não participa do canal e com 403 (somente leitura) se saiu dele. */
    @Transactional
    public void exigirPodeEscrever(Long solicitacaoId, Long profissionalId, Long usuarioId) {
        if (!acessoDe(solicitacaoId, profissionalId, usuarioId).podeEscrever()) {
            throw new ConversaSomenteLeituraException();
        }
    }

    /**
     * Quem recebe em tempo real o que for dito no canal: o paciente e os profissionais que ainda
     * estão na conversa. Quem saiu não recebe mais nada.
     */
    @Transactional(readOnly = true)
    public List<Long> destinatarios(Long solicitacaoId, Long profissionalId) {
        Set<Long> ids = new LinkedHashSet<>();
        chatPropostaRepository.usuarioIdDoPaciente(solicitacaoId).ifPresent(ids::add);
        conversaProfissionalRepository.findBySolicitacaoId(solicitacaoId).stream()
                .filter(ConversaProfissional::ativo)
                .filter(c -> profissionalId == null || c.getUsuarioId().equals(profissionalId))
                .forEach(c -> ids.add(c.getUsuarioId()));
        return List.copyOf(ids);
    }

    /** Canais do usuário, do que recebeu mensagem mais recentemente para o mais antigo. */
    @Transactional
    public List<ConversaResponse> listar(Long usuarioId) {
        Set<Long> solicitacaoIds = new LinkedHashSet<>(
                chatPropostaRepository.solicitacaoIdsComChat(usuarioId, STATUS_QUE_LIBERA_O_CHAT));
        solicitacaoIds.addAll(conversaProfissionalRepository.solicitacaoIdsComoProfissional(usuarioId));
        solicitacaoIds.addAll(conversaProfissionalRepository.solicitacaoIdsComoPaciente(usuarioId));
        if (solicitacaoIds.isEmpty()) {
            return List.of();
        }

        List<Canal> canais = new ArrayList<>();
        Set<Long> todosOsUsuarios = new LinkedHashSet<>();
        for (Long solicitacaoId : solicitacaoIds) {
            Long pacienteId = chatPropostaRepository.usuarioIdDoPaciente(solicitacaoId).orElse(null);
            List<ConversaProfissional> profissionais = sincronizar(solicitacaoId);

            for (ConversaProfissional alvo : profissionais) {
                calcularAcesso(pacienteId, profissionais, alvo.getUsuarioId(), usuarioId)
                        .ifPresent(acesso -> canais.add(
                                new Canal(solicitacaoId, alvo.getUsuarioId(), pacienteId, List.of(alvo), acesso)));
            }
            calcularAcesso(pacienteId, profissionais, null, usuarioId)
                    .ifPresent(acesso -> canais.add(new Canal(solicitacaoId, null, pacienteId, profissionais, acesso)));
        }
        if (canais.isEmpty()) {
            return List.of();
        }

        for (Canal canal : canais) {
            if (canal.pacienteId() != null) {
                todosOsUsuarios.add(canal.pacienteId());
            }
            canal.profissionais().forEach(c -> todosOsUsuarios.add(c.getUsuarioId()));
        }
        Map<Long, Usuario> usuarios = usuarioRepository.findAllById(todosOsUsuarios).stream()
                .collect(Collectors.toMap(Usuario::getId, Function.identity()));
        Map<String, Mensagem> ultimas = mensagemRepository.findUltimasPorCanal(List.copyOf(solicitacaoIds)).stream()
                .collect(Collectors.toMap(
                        m -> chave(m.getSolicitacaoId(), m.getProfissionalUsuarioId()), Function.identity(), (a, b) -> a));
        Map<Long, String> avatares = new HashMap<>();

        return canais.stream()
                .map(canal -> paraResponse(canal, usuarios, avatares, ultimaVisivel(canal, ultimas)))
                .sorted(MAIS_RECENTE_PRIMEIRO)
                .toList();
    }

    /**
     * Cria a participação de quem tem proposta aceita e encerra a de quem deixou de ter. A saída
     * é marcada no instante em que a proposta mudou, para que mensagens escritas depois dela não
     * apareçam para quem saiu. Devolve a lista já atualizada.
     */
    private List<ConversaProfissional> sincronizar(Long solicitacaoId) {
        List<Long> ativos = chatPropostaRepository
                .usuarioIdsDosProfissionais(solicitacaoId, STATUS_QUE_LIBERA_O_CHAT);
        List<ConversaProfissional> participacoes =
                new ArrayList<>(conversaProfissionalRepository.findBySolicitacaoId(solicitacaoId));
        Instant agora = Instant.now();

        for (Long usuarioId : ativos) {
            Optional<ConversaProfissional> existente = participacoes.stream()
                    .filter(c -> c.getUsuarioId().equals(usuarioId))
                    .findFirst();
            if (existente.isEmpty()) {
                ConversaProfissional nova = new ConversaProfissional(solicitacaoId, usuarioId, agora);
                conversaProfissionalRepository.save(nova);
                participacoes.add(nova);
            } else if (!existente.get().ativo()) {
                existente.get().reabrir();
            }
        }

        for (ConversaProfissional participacao : participacoes) {
            if (participacao.ativo() && !ativos.contains(participacao.getUsuarioId())) {
                Instant mudouEm = chatPropostaRepository
                        .ultimaAtualizacaoDaProposta(solicitacaoId, participacao.getUsuarioId());
                participacao.sair(mudouEm == null || mudouEm.isAfter(agora) ? agora : mudouEm);
            }
        }
        return participacoes;
    }

    private static Optional<AcessoAoCanal> calcularAcesso(
            Long pacienteId, List<ConversaProfissional> profissionais, Long profissionalId, Long usuarioId) {
        boolean ehPaciente = usuarioId.equals(pacienteId);
        ConversaProfissional meu = profissionais.stream()
                .filter(c -> c.getUsuarioId().equals(usuarioId))
                .findFirst()
                .orElse(null);
        if (!ehPaciente && meu == null) {
            return Optional.empty();
        }

        if (profissionalId != null) {
            ConversaProfissional alvo = profissionais.stream()
                    .filter(c -> c.getUsuarioId().equals(profissionalId))
                    .findFirst()
                    .orElse(null);
            if (alvo == null || (!ehPaciente && !usuarioId.equals(profissionalId))) {
                return Optional.empty();
            }
            Instant ate = ehPaciente ? null : alvo.getSaiuEm();
            return Optional.of(new AcessoAoCanal(alvo.ativo(), null, ate));
        }

        // O grupo só existe quando há dois ou mais profissionais (ativos ou que já saíram).
        if (profissionais.size() < 2) {
            return Optional.empty();
        }
        if (ehPaciente) {
            return Optional.of(new AcessoAoCanal(profissionais.stream().anyMatch(ConversaProfissional::ativo), null, null));
        }
        return Optional.of(new AcessoAoCanal(meu.ativo(), meu.getEntrouEm(), meu.getSaiuEm()));
    }

    private Mensagem ultimaVisivel(Canal canal, Map<String, Mensagem> ultimasPorCanal) {
        AcessoAoCanal acesso = canal.acesso();
        if (acesso.lerAte() == null) {
            Mensagem ultima = ultimasPorCanal.get(chave(canal.solicitacaoId(), canal.profissionalId()));
            return ultima != null && acesso.enxerga(ultima.getDataEnvio()) ? ultima : null;
        }
        return mensagemRepository.listarDoCanal(canal.solicitacaoId(), canal.profissionalId()).stream()
                .filter(m -> acesso.enxerga(m.getDataEnvio()))
                .reduce((primeira, segunda) -> segunda)
                .orElse(null);
    }

    private ConversaResponse paraResponse(
            Canal canal, Map<Long, Usuario> usuarios, Map<Long, String> avatares, Mensagem ultima) {
        List<ParticipanteResponse> lista = new ArrayList<>();
        if (canal.pacienteId() != null) {
            lista.add(participante(canal.pacienteId(), PAPEL_PACIENTE, true, usuarios, avatares));
        }
        canal.profissionais().forEach(c ->
                lista.add(participante(c.getUsuarioId(), PAPEL_PROFISSIONAL, c.ativo(), usuarios, avatares)));
        return new ConversaResponse(
                canal.solicitacaoId(),
                canal.profissionalId() == null ? TIPO_GRUPO : TIPO_INDIVIDUAL,
                canal.profissionalId(),
                lista,
                !canal.acesso().podeEscrever(),
                ultima == null ? null : UltimaMensagemResponse.de(ultima));
    }

    private ParticipanteResponse participante(
            Long usuarioId, String papel, boolean ativo, Map<Long, Usuario> usuarios, Map<Long, String> avatares) {
        Usuario usuario = usuarios.get(usuarioId);
        String nome = usuario != null ? usuario.getNome() : "Usuário #" + usuarioId;
        return new ParticipanteResponse(usuarioId, perfilDe(usuarioId, papel), nome, papel,
                avatarDe(usuarioId, usuario, avatares), ativo);
    }

    private Long perfilDe(Long usuarioId, String papel) {
        Optional<Long> perfil = PAPEL_PACIENTE.equals(papel)
                ? chatPropostaRepository.pacienteIdDoUsuario(usuarioId)
                : chatPropostaRepository.profissionalIdDoUsuario(usuarioId);
        return perfil.orElse(null);
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

    private static String chave(Long solicitacaoId, Long profissionalId) {
        return solicitacaoId + ":" + (profissionalId == null ? "grupo" : profissionalId);
    }

    private static Instant instanteDaUltimaMensagem(ConversaResponse conversa) {
        UltimaMensagemResponse ultima = conversa.ultimaMensagem();
        return ultima == null || ultima.dataEnvio() == null ? Instant.MIN : ultima.dataEnvio();
    }

    /** Um canal visto pelo usuário logado: grupo ({@code profissionalId} nulo) ou individual. */
    private record Canal(
            Long solicitacaoId,
            Long profissionalId,
            Long pacienteId,
            List<ConversaProfissional> profissionais,
            AcessoAoCanal acesso) {
    }

}
