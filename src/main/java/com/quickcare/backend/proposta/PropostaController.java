package com.quickcare.backend.proposta;

import com.quickcare.backend.proposta.dto.PropostaRequest;
import com.quickcare.backend.proposta.dto.PropostaResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PropostaController {

    private final PropostaService propostaService;

    @PostMapping("/solicitacoes/{solicitacaoId}/propostas")
    public ResponseEntity<PropostaResponse> enviar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long solicitacaoId,
            @Valid @RequestBody PropostaRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propostaService.enviar(usuarioId, solicitacaoId, request));
    }

    @GetMapping("/solicitacoes/{solicitacaoId}/propostas")
    public List<PropostaResponse> listarDaSolicitacao(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long solicitacaoId
    ) {
        return propostaService.listarDaSolicitacao(usuarioId, solicitacaoId);
    }

    @GetMapping("/propostas")
    public List<PropostaResponse> listarMinhas(@AuthenticationPrincipal Long usuarioId) {
        return propostaService.listarDoProfissional(usuarioId);
    }

    @GetMapping("/propostas/{id}")
    public PropostaResponse buscar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return propostaService.buscar(usuarioId, id);
    }

    @PutMapping("/propostas/{id}")
    public PropostaResponse atualizar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody PropostaRequest request
    ) {
        return propostaService.atualizar(usuarioId, id, request);
    }

    @PatchMapping("/propostas/{id}/cancelar")
    public PropostaResponse cancelar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return propostaService.cancelar(usuarioId, id);
    }

    @PatchMapping("/propostas/{id}/recusar")
    public PropostaResponse recusar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return propostaService.recusar(usuarioId, id);
    }

    @PatchMapping("/propostas/{id}/aceitar")
    public PropostaResponse aceitar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return propostaService.aceitar(usuarioId, id);
    }

}
