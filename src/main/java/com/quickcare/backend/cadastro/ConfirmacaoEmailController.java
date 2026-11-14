package com.quickcare.backend.cadastro;

import com.quickcare.backend.auth.AuthCookies;
import com.quickcare.backend.auth.LoginResult;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.cadastro.dto.ConfirmarEmailRequest;
import com.quickcare.backend.cadastro.dto.ReenviarConfirmacaoRequest;
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
@RequestMapping("/auth/confirmacao-email")
@RequiredArgsConstructor
public class ConfirmacaoEmailController {

    private final ConfirmacaoEmailService confirmacaoEmailService;
    private final AuthCookies authCookies;

    @PostMapping("/confirmar")
    public ResponseEntity<LoginResponse> confirmar(@Valid @RequestBody ConfirmarEmailRequest request) {
        LoginResult sessao = confirmacaoEmailService.confirmar(request.token());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.accessToken(sessao.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, authCookies.refreshToken(sessao.refreshToken()).toString())
                .body(sessao.response());
    }

    @PostMapping("/reenviar")
    public MensagemResponse reenviar(@Valid @RequestBody ReenviarConfirmacaoRequest request) {
        confirmacaoEmailService.reenviar(request.email(), request.senha());
        return new MensagemResponse("Enviamos um novo link de confirmação para o seu e-mail.");
    }

}
