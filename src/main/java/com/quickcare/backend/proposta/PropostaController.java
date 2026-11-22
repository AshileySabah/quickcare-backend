package com.quickcare.backend.proposta;

import com.quickcare.backend.proposta.dto.PropostaRequest;
import com.quickcare.backend.proposta.dto.PropostaResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Propostas", description = "Propostas dos profissionais para as solicitações.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class PropostaController {

    private final PropostaService propostaService;

    @Operation(summary = "Profissional envia uma proposta para a solicitação")
    @PostMapping("/solicitacoes/{solicitacaoId}/propostas")
    public ResponseEntity<PropostaResponse> enviar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long solicitacaoId,
            @Valid @RequestBody PropostaRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(propostaService.enviar(usuarioId, solicitacaoId, request));
    }

    @Operation(summary = "Lista as propostas de uma solicitação")
    @GetMapping("/solicitacoes/{solicitacaoId}/propostas")
    public List<PropostaResponse> listarDaSolicitacao(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long solicitacaoId
    ) {
        return propostaService.listarDaSolicitacao(usuarioId, solicitacaoId);
    }

    @Operation(summary = "Lista as propostas do profissional logado")
    @GetMapping("/propostas")
    public List<PropostaResponse> listarMinhas(@AuthenticationPrincipal Long usuarioId) {
        return propostaService.listarDoProfissional(usuarioId);
    }

    @Operation(summary = "Busca uma proposta")
    @GetMapping("/propostas/{id}")
    public PropostaResponse buscar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return propostaService.buscar(usuarioId, id);
    }

    @Operation(summary = "Profissional edita a proposta")
    @PutMapping("/propostas/{id}")
    public PropostaResponse atualizar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody PropostaRequest request
    ) {
        return propostaService.atualizar(usuarioId, id, request);
    }

    @Operation(summary = "Profissional cancela a proposta")
    @PatchMapping("/propostas/{id}/cancelar")
    public PropostaResponse cancelar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return propostaService.cancelar(usuarioId, id);
    }

    @Operation(summary = "Paciente recusa a proposta")
    @PatchMapping("/propostas/{id}/recusar")
    public PropostaResponse recusar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return propostaService.recusar(usuarioId, id);
    }

    @Operation(summary = "Paciente aceita a proposta e gera o atendimento")
    @PatchMapping("/propostas/{id}/aceitar")
    public PropostaResponse aceitar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return propostaService.aceitar(usuarioId, id);
    }

}
