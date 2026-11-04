package com.quickcare.backend.notificacao;

import com.quickcare.backend.notificacao.dto.NaoLidasResponse;
import com.quickcare.backend.notificacao.dto.NotificacaoResponse;
import com.quickcare.backend.usuario.TipoPerfil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    @GetMapping
    public List<NotificacaoResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam TipoPerfil perfil,
            @RequestParam(required = false) Integer limite
    ) {
        return notificacaoService.listar(usuarioId, perfil, limite);
    }

    @GetMapping("/nao-lidas")
    public NaoLidasResponse naoLidas(@AuthenticationPrincipal Long usuarioId) {
        return notificacaoService.contarNaoLidas(usuarioId);
    }

    @PatchMapping("/{id}/lida")
    public NotificacaoResponse marcarComoLida(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return notificacaoService.marcarComoLida(usuarioId, id);
    }

    @PatchMapping("/lidas")
    public NaoLidasResponse marcarTodasComoLidas(@AuthenticationPrincipal Long usuarioId, @RequestParam TipoPerfil perfil) {
        return notificacaoService.marcarTodasComoLidas(usuarioId, perfil);
    }

}
