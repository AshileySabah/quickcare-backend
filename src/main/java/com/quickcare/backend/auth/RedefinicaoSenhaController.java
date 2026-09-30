package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.EsqueciSenhaRequest;
import com.quickcare.backend.auth.dto.RedefinirSenhaRequest;
import com.quickcare.backend.auth.dto.RedefinirSenhaResponse;
import com.quickcare.backend.auth.dto.ValidarTokenRedefinicaoRequest;
import com.quickcare.backend.common.MensagemResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/senha")
@RequiredArgsConstructor
public class RedefinicaoSenhaController {

    private final RedefinicaoSenhaService redefinicaoSenhaService;
    private final AuthCookies authCookies;

    @PostMapping("/esqueci")
    public ResponseEntity<MensagemResponse> esqueci(@Valid @RequestBody EsqueciSenhaRequest request) {
        redefinicaoSenhaService.solicitarRedefinicao(request.email());
        return ResponseEntity.ok(new MensagemResponse(
                "Se o e-mail estiver cadastrado, você receberá um link para redefinir a senha."));
    }

    @PostMapping("/validar-token")
    public ResponseEntity<Void> validarToken(@Valid @RequestBody ValidarTokenRedefinicaoRequest request) {
        redefinicaoSenhaService.validarToken(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/redefinir")
    public ResponseEntity<RedefinirSenhaResponse> redefinir(@Valid @RequestBody RedefinirSenhaRequest request) {
        LoginResult sessao = redefinicaoSenhaService.redefinirSenha(request.token(), request.novaSenha());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.accessToken(sessao.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, authCookies.refreshToken(sessao.refreshToken()).toString())
                .body(new RedefinirSenhaResponse("Senha redefinida com sucesso.", sessao.response()));
    }

}
