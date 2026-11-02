package com.quickcare.backend.solicitacao;

import com.quickcare.backend.solicitacao.dto.SolicitacaoRequest;
import com.quickcare.backend.solicitacao.dto.SolicitacaoResponse;
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

/**
 * CRUD de solicitações. A exclusão é lógica via {@code /cancelar}, para que o
 * histórico do paciente e as propostas vinculadas não se percam.
 */
@RestController
@RequestMapping("/solicitacoes")
@RequiredArgsConstructor
public class SolicitacaoController {

    private final SolicitacaoService solicitacaoService;

    @PostMapping
    public ResponseEntity<SolicitacaoResponse> criar(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestBody SolicitacaoRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitacaoService.criar(usuarioId, request));
    }

    @GetMapping
    public List<SolicitacaoResponse> listarMinhas(@AuthenticationPrincipal Long usuarioId) {
        return solicitacaoService.listarDoPaciente(usuarioId);
    }

    @GetMapping("/disponiveis")
    public List<SolicitacaoResponse> listarDisponiveis(@AuthenticationPrincipal Long usuarioId) {
        return solicitacaoService.listarDisponiveis(usuarioId);
    }

    @GetMapping("/{id}")
    public SolicitacaoResponse buscar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return solicitacaoService.buscar(usuarioId, id);
    }

    @PutMapping("/{id}")
    public SolicitacaoResponse atualizar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody SolicitacaoRequest request
    ) {
        return solicitacaoService.atualizar(usuarioId, id, request);
    }

    @PatchMapping("/{id}/cancelar")
    public SolicitacaoResponse cancelar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return solicitacaoService.cancelar(usuarioId, id);
    }

}
