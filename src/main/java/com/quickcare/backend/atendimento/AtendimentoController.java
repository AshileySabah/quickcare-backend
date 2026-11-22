package com.quickcare.backend.atendimento;

import com.quickcare.backend.atendimento.dto.AtendimentoResponse;
import com.quickcare.backend.atendimento.dto.RemarcacaoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Atendimentos", description = "Reaceite, remarcação e cancelamento do atendimento gerado por uma proposta aceita.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/atendimentos/{id}")
@RequiredArgsConstructor
public class AtendimentoController {

    private final AtendimentoService atendimentoService;

    @Operation(summary = "Profissional reaceita a nova versão da solicitação")
    @PatchMapping("/reaceitar")
    public AtendimentoResponse reaceitar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return atendimentoService.reaceitar(usuarioId, id);
    }

    @Operation(summary = "Profissional recusa a nova versão da solicitação, sem penalidade")
    @PatchMapping("/recusar-reaceite")
    public AtendimentoResponse recusarReaceite(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return atendimentoService.recusarReaceite(usuarioId, id);
    }

    @Operation(summary = "Pede remarcação para um novo período")
    @PostMapping("/remarcacao")
    public AtendimentoResponse solicitarRemarcacao(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody RemarcacaoRequest request
    ) {
        return atendimentoService.solicitarRemarcacao(usuarioId, id, request);
    }

    @Operation(summary = "Aceita a remarcação pedida pela outra parte")
    @PatchMapping("/remarcacao/aceitar")
    public AtendimentoResponse aceitarRemarcacao(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return atendimentoService.responderRemarcacao(usuarioId, id, true);
    }

    @Operation(summary = "Recusa a remarcação e mantém o período atual")
    @PatchMapping("/remarcacao/recusar")
    public AtendimentoResponse recusarRemarcacao(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return atendimentoService.responderRemarcacao(usuarioId, id, false);
    }

    @Operation(summary = "Cancela o atendimento (pode gerar ocorrência fora da tolerância de 48h)")
    @PatchMapping("/cancelar")
    public AtendimentoResponse cancelar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return atendimentoService.cancelar(usuarioId, id);
    }

}
