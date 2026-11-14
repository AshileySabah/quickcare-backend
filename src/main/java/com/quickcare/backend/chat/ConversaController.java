package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.ConversaResponse;
import com.quickcare.backend.chat.dto.NaoLidasChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/conversas")
@RequiredArgsConstructor
public class ConversaController {

    private final ConversaService conversaService;
    private final MensagemNaoLidaService mensagemNaoLidaService;

    @GetMapping
    public List<ConversaResponse> listar(@AuthenticationPrincipal Long usuarioId) {
        return conversaService.listar(usuarioId);
    }

    @GetMapping("/nao-lidas")
    public NaoLidasChatResponse naoLidas(@AuthenticationPrincipal Long usuarioId) {
        return mensagemNaoLidaService.contar(usuarioId);
    }

    @PostMapping("/{solicitacaoId}/lida")
    public NaoLidasChatResponse marcarComoLida(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long solicitacaoId,
            @RequestParam(required = false) Long profissionalUsuarioId) {
        return mensagemNaoLidaService.marcarComoLido(usuarioId, solicitacaoId, profissionalUsuarioId);
    }

}
