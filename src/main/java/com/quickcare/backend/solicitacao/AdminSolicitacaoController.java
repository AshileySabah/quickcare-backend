package com.quickcare.backend.solicitacao;

import com.quickcare.backend.solicitacao.dto.AlterarStatusAdminRequest;
import com.quickcare.backend.solicitacao.dto.SolicitacaoAdminDetalheResponse;
import com.quickcare.backend.solicitacao.dto.SolicitacaoAdminResumoResponse;
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
@RequestMapping("/admin/solicitacoes")
@RequiredArgsConstructor
public class AdminSolicitacaoController {

    private final AdminSolicitacaoService adminSolicitacaoService;

    @GetMapping
    public List<SolicitacaoAdminResumoResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam(required = false) List<StatusSolicitacao> status,
            @RequestParam(required = false) String busca
    ) {
        return adminSolicitacaoService.listar(usuarioId, status, busca);
    }

    @GetMapping("/{id}")
    public SolicitacaoAdminDetalheResponse detalhar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return adminSolicitacaoService.detalhar(usuarioId, id);
    }

    @PatchMapping("/{id}/status")
    public SolicitacaoAdminDetalheResponse alterarStatus(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody AlterarStatusAdminRequest request
    ) {
        return adminSolicitacaoService.alterarStatus(usuarioId, id, request.acao(), request.justificativa());
    }

}
