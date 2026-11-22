package com.quickcare.backend.cadastro;

import com.quickcare.backend.auth.LoginResult;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.cadastro.dto.ConfirmarEmailRequest;
import com.quickcare.backend.cadastro.dto.ReenviarConfirmacaoRequest;
import com.quickcare.backend.common.MensagemResponse;
import com.quickcare.backend.jwt.AutenticacaoPorToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Confirmação de e-mail", description = "Confirmação do e-mail depois do cadastro.")
@RestController
@RequestMapping("/auth/confirmacao-email")
@RequiredArgsConstructor
public class ConfirmacaoEmailController {

    private final ConfirmacaoEmailService confirmacaoEmailService;

    @Operation(summary = "Confirma o e-mail pelo link e já inicia a sessão")
    @PostMapping("/confirmar")
    public ResponseEntity<LoginResponse> confirmar(@Valid @RequestBody ConfirmarEmailRequest request) {
        LoginResult sessao = confirmacaoEmailService.confirmar(request.token());
        return ResponseEntity.ok()
                .header(AutenticacaoPorToken.HEADER_TOKEN, sessao.token())
                .body(sessao.response());
    }

    @Operation(summary = "Reenvia o link de confirmação")
    @PostMapping("/reenviar")
    public MensagemResponse reenviar(@Valid @RequestBody ReenviarConfirmacaoRequest request) {
        confirmacaoEmailService.reenviar(request.email(), request.senha());
        return new MensagemResponse("Enviamos um novo link de confirmação para o seu e-mail.");
    }

}
