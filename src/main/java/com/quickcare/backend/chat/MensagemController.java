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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Mensagens dos canais. O envio é via HTTP (persistido) e os demais participantes recebem em
 * tempo real assinando {@code /user/queue/mensagens} no WebSocket (autenticado). Sem
 * {@code profissionalUsuarioId} o canal é o grupo; com ele, a conversa individual com esse
 * profissional. Envio e histórico respondem 403 se o usuário não participa do canal, e o envio
 * também responde 403 para quem já saiu da conversa (só leitura).
 */
@RestController
@RequiredArgsConstructor
public class MensagemController {

    private final MensagemService mensagemService;

    @PostMapping(path = "/mensagens", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MensagemChatResponse enviar(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestPart("dados") EnviarMensagemRequest request,
            @RequestPart(value = "arquivo", required = false) MultipartFile arquivo) {
        return mensagemService.enviar(usuarioId, request, arquivo);
    }

    @GetMapping("/conversas/{solicitacaoId}/mensagens")
    public List<MensagemChatResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long solicitacaoId,
            @RequestParam(required = false) Long profissionalUsuarioId) {
        return mensagemService.listarPorConversa(usuarioId, solicitacaoId, profissionalUsuarioId);
    }

    @DeleteMapping("/mensagens/{id}")
    public ResponseEntity<Void> excluir(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        mensagemService.excluir(usuarioId, id);
        return ResponseEntity.noContent().build();
    }

}
