package com.quickcare.backend.notificacao;

import com.quickcare.backend.notificacao.dto.NaoLidasResponse;
import com.quickcare.backend.notificacao.dto.NotificacaoResponse;
import com.quickcare.backend.usuario.TipoPerfil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Notificações", description = "Notificações do sino, separadas por perfil.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    @Operation(summary = "Lista as notificações do perfil")
    @GetMapping
    public List<NotificacaoResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam TipoPerfil perfil,
            @RequestParam(required = false) Integer limite
    ) {
        return notificacaoService.listar(usuarioId, perfil, limite);
    }

    @Operation(summary = "Conta as não lidas de cada perfil")
    @GetMapping("/nao-lidas")
    public NaoLidasResponse naoLidas(@AuthenticationPrincipal Long usuarioId) {
        return notificacaoService.contarNaoLidas(usuarioId);
    }

    @Operation(summary = "Marca uma notificação como lida")
    @PatchMapping("/{id}/lida")
    public NotificacaoResponse marcarComoLida(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return notificacaoService.marcarComoLida(usuarioId, id);
    }

    @Operation(summary = "Marca todas as notificações do perfil como lidas")
    @PatchMapping("/lidas")
    public NaoLidasResponse marcarTodasComoLidas(@AuthenticationPrincipal Long usuarioId, @RequestParam TipoPerfil perfil) {
        return notificacaoService.marcarTodasComoLidas(usuarioId, perfil);
    }

}
