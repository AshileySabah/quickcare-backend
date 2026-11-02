package com.quickcare.backend.problema;

import com.quickcare.backend.problema.dto.DecisaoDisputaRequest;
import com.quickcare.backend.problema.dto.DisputaDetalheResponse;
import com.quickcare.backend.problema.dto.DisputaResumoResponse;
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

/** Relatos de "não foi realizado" e disputas, para o admin decidir. */
@RestController
@RequestMapping("/admin/disputas")
@RequiredArgsConstructor
public class AdminDisputaController {

    private final AdminDisputaService adminDisputaService;

    /** {@code status} pode repetir; sem ele, todos. */
    @GetMapping
    public List<DisputaResumoResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam(required = false) List<StatusProblema> status
    ) {
        return adminDisputaService.listar(usuarioId, status);
    }

    /** Para o contador no menu do admin. */
    @GetMapping("/em-aberto")
    public Map<String, Long> emAberto(@AuthenticationPrincipal Long usuarioId) {
        return Map.of("total", adminDisputaService.contarEmDisputa(usuarioId));
    }

    @GetMapping("/{id}")
    public DisputaDetalheResponse detalhar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return adminDisputaService.detalhar(usuarioId, id);
    }

    @PostMapping("/{id}/decisao")
    public DisputaDetalheResponse decidir(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody DecisaoDisputaRequest request
    ) {
        return adminDisputaService.decidir(usuarioId, id, request);
    }

}
