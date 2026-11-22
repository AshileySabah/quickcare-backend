package com.quickcare.backend.solicitacao;

import com.quickcare.backend.solicitacao.dto.SolicitacaoRequest;
import com.quickcare.backend.solicitacao.dto.SolicitacaoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Solicitações", description = "Pedidos de atendimento criados pelos pacientes.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/solicitacoes")
@RequiredArgsConstructor
public class SolicitacaoController {

    private final SolicitacaoService solicitacaoService;

    @Operation(summary = "Paciente cria uma solicitação")
    @PostMapping
    public ResponseEntity<SolicitacaoResponse> criar(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestBody SolicitacaoRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitacaoService.criar(usuarioId, request));
    }

    @Operation(summary = "Lista as solicitações do paciente logado")
    @GetMapping
    public List<SolicitacaoResponse> listarMinhas(@AuthenticationPrincipal Long usuarioId) {
        return solicitacaoService.listarDoPaciente(usuarioId);
    }

    @Operation(summary = "Lista as solicitações abertas para o profissional logado")
    @GetMapping("/disponiveis")
    public List<SolicitacaoResponse> listarDisponiveis(@AuthenticationPrincipal Long usuarioId) {
        return solicitacaoService.listarDisponiveis(usuarioId);
    }

    @Operation(summary = "Busca uma solicitação")
    @GetMapping("/{id}")
    public SolicitacaoResponse buscar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return solicitacaoService.buscar(usuarioId, id);
    }

    @Operation(summary = "Paciente edita a solicitação")
    @PutMapping("/{id}")
    public SolicitacaoResponse atualizar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody SolicitacaoRequest request
    ) {
        return solicitacaoService.atualizar(usuarioId, id, request);
    }

    @Operation(summary = "Paciente cancela a solicitação")
    @PatchMapping("/{id}/cancelar")
    public SolicitacaoResponse cancelar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return solicitacaoService.cancelar(usuarioId, id);
    }

}
