package com.quickcare.backend.seguranca;

import com.quickcare.backend.auth.LoginResult;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.jwt.AutenticacaoPorToken;
import com.quickcare.backend.seguranca.dto.RecuperarContaRequest;
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

@Tag(name = "Segurança · Recuperar conta", description = "Recuperação de conta encerrada dentro do prazo de 30 dias.")
@RestController
@RequestMapping("/auth/conta")
@RequiredArgsConstructor
public class RecuperacaoContaController {

    private final SegurancaService segurancaService;

    @Operation(summary = "Recupera a conta encerrada com e-mail e senha")
    @PostMapping("/recuperar")
    public ResponseEntity<LoginResponse> recuperar(@Valid @RequestBody RecuperarContaRequest request) {
        return comSessao(segurancaService.recuperarConta(request));
    }

    @Operation(summary = "Recupera a conta encerrada pelo link do e-mail")
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
