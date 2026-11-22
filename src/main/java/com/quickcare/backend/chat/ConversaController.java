package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.ConversaResponse;
import com.quickcare.backend.chat.dto.NaoLidasChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Chat · Conversas", description = "Conversas individuais e em grupo de cada solicitação com proposta aceita.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/conversas")
@RequiredArgsConstructor
public class ConversaController {

    private final ConversaService conversaService;
    private final MensagemNaoLidaService mensagemNaoLidaService;

    @Operation(summary = "Lista as conversas do usuário, com busca por nome ou mensagem")
    @GetMapping
    public List<ConversaResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam(required = false) String busca) {
        return conversaService.listar(usuarioId, busca);
    }

    @Operation(summary = "Conta as mensagens não lidas por conversa")
    @GetMapping("/nao-lidas")
    public NaoLidasChatResponse naoLidas(@AuthenticationPrincipal Long usuarioId) {
        return mensagemNaoLidaService.contar(usuarioId);
    }

    @Operation(summary = "Marca a conversa como lida")
    @PostMapping("/{solicitacaoId}/lida")
    public NaoLidasChatResponse marcarComoLida(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long solicitacaoId,
            @RequestParam(required = false) Long profissionalUsuarioId) {
        return mensagemNaoLidaService.marcarComoLido(usuarioId, solicitacaoId, profissionalUsuarioId);
    }

}
