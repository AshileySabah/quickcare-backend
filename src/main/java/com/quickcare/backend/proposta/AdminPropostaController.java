package com.quickcare.backend.proposta;

import com.quickcare.backend.proposta.dto.AlterarStatusPropostaAdminRequest;
import com.quickcare.backend.proposta.dto.PropostaAdminResponse;
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

@RestController
@RequestMapping("/admin/propostas")
@RequiredArgsConstructor
public class AdminPropostaController {

    private final AdminPropostaService adminPropostaService;

    @GetMapping
    public List<PropostaAdminResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam(required = false) List<StatusProposta> status,
            @RequestParam(required = false) String busca
    ) {
        return adminPropostaService.listar(usuarioId, status, busca);
    }

    @PatchMapping("/{id}/status")
    public PropostaAdminResponse alterarStatus(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody AlterarStatusPropostaAdminRequest request
    ) {
        return adminPropostaService.alterarStatus(usuarioId, id, request.acao(), request.justificativa());
    }

}
