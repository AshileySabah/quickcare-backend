package com.quickcare.backend.notificacao;

import com.quickcare.backend.notificacao.dto.NaoLidasResponse;
import com.quickcare.backend.notificacao.dto.NotificacaoResponse;
import com.quickcare.backend.notificacao.exception.NotificacaoNaoEncontradaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Grava as notificações de cada perfil e as entrega em tempo real. Os textos
 * de cada evento ficam no {@link Notificador}.
 */
@Service
@RequiredArgsConstructor
public class NotificacaoService {

    static final int LIMITE_PADRAO = 30;
    private static final int LIMITE_MAXIMO = 100;

    private final NotificacaoRepository notificacaoRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Participa da transação de quem chama: se ela for desfeita, a notificação também é. */
    @Transactional
    public void notificar(
            Usuario usuario,
            TipoPerfil perfil,
            TipoNotificacao tipo,
            String titulo,
            String mensagem,
            String link,
            boolean comEmail
    ) {
        Notificacao notificacao = notificacaoRepository.save(Notificacao.builder()
                .usuario(usuario)
                .perfil(perfil)
                .tipo(tipo)
                .titulo(titulo)
                .mensagem(mensagem)
                .link(link)
                .build());

        NotificacaoCriada.Email email = comEmail
                ? new NotificacaoCriada.Email(usuario.getEmail(), usuario.getNome())
                : null;
        eventPublisher.publishEvent(new NotificacaoCriada(usuario.getId(), NotificacaoResponse.de(notificacao), email));
    }

    @Transactional(readOnly = true)
    public List<NotificacaoResponse> listar(Long usuarioId, TipoPerfil perfil, Integer limite) {
        int tamanho = limite == null ? LIMITE_PADRAO : Math.clamp(limite, 1, LIMITE_MAXIMO);
        return notificacaoRepository
                .findByUsuarioIdAndPerfilOrderByCriadoEmDescIdDesc(usuarioId, perfil, PageRequest.of(0, tamanho))
                .stream()
                .map(NotificacaoResponse::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public NaoLidasResponse contarNaoLidas(Long usuarioId) {
        return new NaoLidasResponse(
                notificacaoRepository.countByUsuarioIdAndPerfilAndLidaEmIsNull(usuarioId, TipoPerfil.PACIENTE),
                notificacaoRepository.countByUsuarioIdAndPerfilAndLidaEmIsNull(usuarioId, TipoPerfil.PROFISSIONAL));
    }

    @Transactional
    public NotificacaoResponse marcarComoLida(Long usuarioId, Long id) {
        Notificacao notificacao = notificacaoRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(NotificacaoNaoEncontradaException::new);
        notificacao.marcarComoLida();
        return NotificacaoResponse.de(notificacao);
    }

    @Transactional
    public NaoLidasResponse marcarTodasComoLidas(Long usuarioId, TipoPerfil perfil) {
        notificacaoRepository.marcarTodasComoLidas(usuarioId, perfil, Instant.now());
        return contarNaoLidas(usuarioId);
    }

}
