package com.quickcare.backend.chat;

import com.quickcare.backend.chat.dto.EnviarMensagemRequest;
import com.quickcare.backend.chat.dto.MensagemChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Chat entre usuários. O envio é via HTTP (persistido) e o destinatário recebe
 * em tempo real assinando {@code /user/queue/mensagens} no WebSocket (autenticado).
 */
@RestController
@RequestMapping("/mensagens")
@RequiredArgsConstructor
public class MensagemController {

    private final MensagemService mensagemService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MensagemChatResponse enviar(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestPart("dados") EnviarMensagemRequest request,
            @RequestPart(value = "arquivo", required = false) MultipartFile arquivo
    ) {
        return mensagemService.enviar(usuarioId, request, arquivo);
    }

    @GetMapping
    public List<MensagemChatResponse> listar(@AuthenticationPrincipal Long usuarioId) {
        return mensagemService.listar(usuarioId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        mensagemService.excluir(usuarioId, id);
        return ResponseEntity.noContent().build();
    }

}
