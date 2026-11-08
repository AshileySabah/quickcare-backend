package com.quickcare.backend.seguranca;

import com.quickcare.backend.seguranca.dto.DesfazerEmailRequest;
import com.quickcare.backend.seguranca.dto.DesfazerEmailResponse;
import com.quickcare.backend.seguranca.dto.RecuperarPorLinkRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/email/desfazer")
@RequiredArgsConstructor
public class DesfazerEmailController {

    private final SegurancaService segurancaService;

    @PostMapping("/validar")
    public ResponseEntity<Void> validar(@Valid @RequestBody RecuperarPorLinkRequest request) {
        segurancaService.validarDesfazerEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public DesfazerEmailResponse desfazer(@Valid @RequestBody DesfazerEmailRequest request) {
        return new DesfazerEmailResponse(segurancaService.desfazerTrocaDeEmail(request.token(), request.senha()));
    }

}
