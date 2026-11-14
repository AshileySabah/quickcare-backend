package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.NaoLidasChatResponse;
import com.quickcare.backend.common.EmailService;
import com.quickcare.backend.notificacao.NotificacaoService;
import com.quickcare.backend.notificacao.TipoNotificacao;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MensagemNaoLidaService {

    static final Duration PRAZO_AVISO_EMAIL = Duration.ofHours(1);

    private final MensagemNaoLidaRepository mensagemNaoLidaRepository;
    private final ChatPropostaRepository chatPropostaRepository;
    private final ConversaService conversaService;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;
    private final EmailService emailService;
    private final Clock clock;

    @Value("${quickcare.frontend-url}")
    private String frontendUrl;

    @Transactional
    public void registrar(Mensagem mensagem, Collection<Long> destinatarios) {
        Long pacienteUsuarioId = chatPropostaRepository.usuarioIdDoPaciente(mensagem.getSolicitacaoId()).orElse(null);
        String remetente = usuarioRepository.findById(mensagem.getRemetenteId())
                .map(Usuario::getNome)
                .orElse("Alguém");
        Instant agora = clock.instant();

        for (Long destinatarioId : destinatarios) {
            if (destinatarioId.equals(mensagem.getRemetenteId())) {
                continue;
            }
            boolean canalJaTinhaNaoLidas = mensagemNaoLidaRepository.existeNoCanal(
                    destinatarioId, mensagem.getSolicitacaoId(), mensagem.getProfissionalUsuarioId());
            mensagemNaoLidaRepository.save(new MensagemNaoLida(mensagem, destinatarioId, agora));
            if (!canalJaTinhaNaoLidas) {
                TipoPerfil perfil = destinatarioId.equals(pacienteUsuarioId) ? TipoPerfil.PACIENTE : TipoPerfil.PROFISSIONAL;
                usuarioRepository.findById(destinatarioId).ifPresent(usuario -> notificacaoService.notificar(
                        usuario, perfil, TipoNotificacao.MENSAGEM_RECEBIDA, "Nova mensagem de " + remetente,
                        remetente + " enviou uma mensagem " + (mensagem.getProfissionalUsuarioId() == null ? "no grupo" : "na conversa")
                                + " da solicitação #" + mensagem.getSolicitacaoId() + ".",
                        linkDoCanal(perfil, mensagem.getSolicitacaoId(), mensagem.getProfissionalUsuarioId()), false));
            }
        }
    }

    @Transactional(readOnly = true)
    public NaoLidasChatResponse contar(Long usuarioId) {
        List<NaoLidasChatResponse.Canal> canais = mensagemNaoLidaRepository.contarPorCanal(usuarioId).stream()
                .map(c -> new NaoLidasChatResponse.Canal(c.getSolicitacaoId(), c.getProfissionalUsuarioId(), c.getQuantidade()))
                .toList();
        long total = canais.stream().mapToLong(NaoLidasChatResponse.Canal::quantidade).sum();
        return new NaoLidasChatResponse(total, canais);
    }

    @Transactional
    public NaoLidasChatResponse marcarComoLido(Long usuarioId, Long solicitacaoId, Long profissionalUsuarioId) {
        conversaService.acessoDe(solicitacaoId, profissionalUsuarioId, usuarioId);
        mensagemNaoLidaRepository.marcarCanalComoLido(usuarioId, solicitacaoId, profissionalUsuarioId);
        return contar(usuarioId);
    }

    @Transactional
    public void excluirDaMensagem(Long mensagemId) {
        mensagemNaoLidaRepository.excluirDaMensagem(mensagemId);
    }

    @Transactional
    public void avisarPorEmail() {
        Instant agora = clock.instant();
        for (Long usuarioId : mensagemNaoLidaRepository.usuariosComAvisoPendente(agora.minus(PRAZO_AVISO_EMAIL))) {
            List<MensagemNaoLida> pendentes = mensagemNaoLidaRepository.findByUsuarioIdAndEmailEnviadoEmIsNull(usuarioId);
            if (pendentes.isEmpty()) {
                continue;
            }
            usuarioRepository.findById(usuarioId)
                    .filter(Usuario::recebeNotificacoes)
                    .ifPresent(usuario -> enviarAviso(usuario, pendentes));
            pendentes.forEach(pendente -> pendente.marcarEmailEnviado(agora));
        }
    }

    private void enviarAviso(Usuario usuario, List<MensagemNaoLida> pendentes) {
        MensagemNaoLida primeira = pendentes.getFirst();
        boolean ehPaciente = chatPropostaRepository.usuarioIdDoPaciente(primeira.getSolicitacaoId())
                .map(usuario.getId()::equals)
                .orElse(false);
        TipoPerfil perfil = ehPaciente ? TipoPerfil.PACIENTE : TipoPerfil.PROFISSIONAL;
        int quantidade = pendentes.size();

        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("nome", usuario.getNome());
        variaveis.put("titulo", "Você tem mensagens não lidas");
        variaveis.put("mensagem", quantidade == 1
                ? "Você tem 1 mensagem não lida no chat do QuickCare há mais de 1 hora."
                : "Você tem " + quantidade + " mensagens não lidas no chat do QuickCare há mais de 1 hora.");
        variaveis.put("link", UriComponentsBuilder.fromUriString(frontendUrl)
                .path(linkDoCanal(perfil, primeira.getSolicitacaoId(), primeira.getProfissionalUsuarioId()))
                .toUriString());
        try {
            emailService.enviarEmailTemplate(usuario.getEmail(), "Você tem mensagens não lidas - QuickCare",
                    "notificacao", variaveis);
        } catch (RuntimeException ex) {
            log.warn("Não foi possível avisar o usuário {} sobre mensagens não lidas.", usuario.getId(), ex);
        }
    }

    static String linkDoCanal(TipoPerfil perfil, Long solicitacaoId, Long profissionalUsuarioId) {
        String base = perfil == TipoPerfil.PACIENTE ? "/patient/mensagens/" : "/professional/mensagens/";
        return base + solicitacaoId + "/" + (profissionalUsuarioId == null ? "grupo" : profissionalUsuarioId);
    }

}
