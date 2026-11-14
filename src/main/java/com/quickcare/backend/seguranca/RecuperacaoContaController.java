package com.quickcare.backend.seguranca;

import com.quickcare.backend.auth.LoginResult;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.jwt.AutenticacaoPorToken;
import com.quickcare.backend.seguranca.dto.RecuperarContaRequest;
import com.quickcare.backend.seguranca.dto.RecuperarPorLinkRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/conta")
@RequiredArgsConstructor
public class RecuperacaoContaController {

    private final SegurancaService segurancaService;

    @PostMapping("/recuperar")
    public ResponseEntity<LoginResponse> recuperar(@Valid @RequestBody RecuperarContaRequest request) {
        return comSessao(segurancaService.recuperarConta(request));
    }

    @PostMapping("/recuperar-por-link")
    public ResponseEntity<LoginResponse> recuperarPorLink(@Valid @RequestBody RecuperarPorLinkRequest request) {
        return comSessao(segurancaService.recuperarPorLink(request.token()));
    }

    private ResponseEntity<LoginResponse> comSessao(LoginResult sessao) {
        return ResponseEntity.ok()
                .header(AutenticacaoPorToken.HEADER_TOKEN, sessao.token())
                .body(sessao.response());
    }

}
