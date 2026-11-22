package com.quickcare.backend.proposta;

import com.quickcare.backend.proposta.dto.AlterarStatusPropostaAdminRequest;
import com.quickcare.backend.proposta.dto.PropostaAdminResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin · Propostas", description = "Propostas vistas e alteradas pelo administrador.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/propostas")
@RequiredArgsConstructor
public class AdminPropostaController {

    private final AdminPropostaService adminPropostaService;

    @Operation(summary = "Lista as propostas com filtros")
    @GetMapping
    public List<PropostaAdminResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam(required = false) List<StatusProposta> status,
            @RequestParam(required = false) String busca
    ) {
        return adminPropostaService.listar(usuarioId, status, busca);
    }

    @Operation(summary = "Recusa, cancela ou reativa uma proposta")
    @PatchMapping("/{id}/status")
    public PropostaAdminResponse alterarStatus(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody AlterarStatusPropostaAdminRequest request
    ) {
        return adminPropostaService.alterarStatus(usuarioId, id, request.acao(), request.justificativa());
    }

}
