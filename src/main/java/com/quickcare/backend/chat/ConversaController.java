package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.ConversaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Conversas do usuário logado: uma por solicitação com proposta aceita. Não há criação manual,
 * porque a conversa passa a existir quando a proposta é aceita.
 */
@RestController
@RequestMapping("/conversas")
@RequiredArgsConstructor
public class ConversaController {

    private final ConversaService conversaService;

    @GetMapping
    public List<ConversaResponse> listar(@AuthenticationPrincipal Long usuarioId) {
        return conversaService.listar(usuarioId);
    }

}
