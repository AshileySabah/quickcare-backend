package com.quickcare.backend.problema;

import com.quickcare.backend.problema.dto.DecisaoDisputaRequest;
import com.quickcare.backend.problema.dto.DisputaDetalheResponse;
import com.quickcare.backend.problema.dto.DisputaResumoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Admin · Disputas", description = "Problemas de atendimento contestados que o administrador decide.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/disputas")
@RequiredArgsConstructor
public class AdminDisputaController {

    private final AdminDisputaService adminDisputaService;

    @Operation(summary = "Lista as disputas por situação")
    @GetMapping
    public List<DisputaResumoResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam(required = false) List<StatusProblema> status
    ) {
        return adminDisputaService.listar(usuarioId, status);
    }

    @Operation(summary = "Conta as disputas aguardando decisão")
    @GetMapping("/em-aberto")
    public Map<String, Long> emAberto(@AuthenticationPrincipal Long usuarioId) {
        return Map.of("total", adminDisputaService.contarEmDisputa(usuarioId));
    }

    @Operation(summary = "Detalha uma disputa")
    @GetMapping("/{id}")
    public DisputaDetalheResponse detalhar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return adminDisputaService.detalhar(usuarioId, id);
    }

    @Operation(summary = "Decide se o atendimento foi realizado e quem leva ocorrência")
    @PostMapping("/{id}/decisao")
    public DisputaDetalheResponse decidir(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody DecisaoDisputaRequest request
    ) {
        return adminDisputaService.decidir(usuarioId, id, request);
    }

}
