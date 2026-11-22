package com.quickcare.backend.seguranca;

import com.quickcare.backend.seguranca.dto.DesfazerEmailRequest;
import com.quickcare.backend.seguranca.dto.DesfazerEmailResponse;
import com.quickcare.backend.seguranca.dto.RecuperarPorLinkRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Segurança · Desfazer troca de e-mail", description = "Link enviado ao e-mail antigo para desfazer uma troca de e-mail.")
@RestController
@RequestMapping("/auth/email/desfazer")
@RequiredArgsConstructor
public class DesfazerEmailController {

    private final SegurancaService segurancaService;

    @Operation(summary = "Confere se o link de desfazer ainda é válido")
    @PostMapping("/validar")
    public ResponseEntity<Void> validar(@Valid @RequestBody RecuperarPorLinkRequest request) {
        segurancaService.validarDesfazerEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Desfaz a troca de e-mail e devolve o acesso")
    @PostMapping
    public DesfazerEmailResponse desfazer(@Valid @RequestBody DesfazerEmailRequest request) {
        return new DesfazerEmailResponse(segurancaService.desfazerTrocaDeEmail(request.token(), request.senha()));
    }

}
