package com.quickcare.backend.atendimento;

import com.quickcare.backend.atendimento.dto.AtendimentoResponse;
import com.quickcare.backend.atendimento.dto.RemarcacaoRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ações sobre o atendimento de uma proposta aceita. Ele vem dentro da
 * proposta ({@code atendimento}); aqui só as mudanças.
 */
@RestController
@RequestMapping("/atendimentos/{id}")
@RequiredArgsConstructor
public class AtendimentoController {

    private final AtendimentoService atendimentoService;

    /** O profissional aceita a nova versão da solicitação editada pelo paciente. */
    @PatchMapping("/reaceitar")
    public AtendimentoResponse reaceitar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return atendimentoService.reaceitar(usuarioId, id);
    }

    /** O profissional recusa a nova versão, sem penalidade. */
    @PatchMapping("/recusar-reaceite")
    public AtendimentoResponse recusarReaceite(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return atendimentoService.recusarReaceite(usuarioId, id);
    }

    @PostMapping("/remarcacao")
    public AtendimentoResponse solicitarRemarcacao(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody RemarcacaoRequest request
    ) {
        return atendimentoService.solicitarRemarcacao(usuarioId, id, request);
    }

    @PatchMapping("/remarcacao/aceitar")
    public AtendimentoResponse aceitarRemarcacao(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return atendimentoService.responderRemarcacao(usuarioId, id, true);
    }

    @PatchMapping("/remarcacao/recusar")
    public AtendimentoResponse recusarRemarcacao(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return atendimentoService.responderRemarcacao(usuarioId, id, false);
    }

    @PatchMapping("/cancelar")
    public AtendimentoResponse cancelar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return atendimentoService.cancelar(usuarioId, id);
    }

}
