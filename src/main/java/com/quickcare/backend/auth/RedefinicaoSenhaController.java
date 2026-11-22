package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.EsqueciSenhaRequest;
import com.quickcare.backend.auth.dto.RedefinirSenhaRequest;
import com.quickcare.backend.auth.dto.RedefinirSenhaResponse;
import com.quickcare.backend.auth.dto.ValidarTokenRedefinicaoRequest;
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

@Tag(name = "Redefinição de senha", description = "Fluxo de esqueci minha senha por link enviado ao e-mail.")
@RestController
@RequestMapping("/auth/senha")
@RequiredArgsConstructor
public class RedefinicaoSenhaController {

    private final RedefinicaoSenhaService redefinicaoSenhaService;

    @Operation(summary = "Envia o link de redefinição de senha por e-mail")
    @PostMapping("/esqueci")
    public ResponseEntity<MensagemResponse> esqueci(@Valid @RequestBody EsqueciSenhaRequest request) {
        redefinicaoSenhaService.solicitarRedefinicao(request.email());
        return ResponseEntity.ok(new MensagemResponse(
                "Se o e-mail estiver cadastrado, você receberá um link para redefinir a senha."));
    }

    @Operation(summary = "Confere se o link de redefinição ainda é válido")
    @PostMapping("/validar-token")
    public ResponseEntity<Void> validarToken(@Valid @RequestBody ValidarTokenRedefinicaoRequest request) {
        redefinicaoSenhaService.validarToken(request.token());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Define a nova senha a partir do link")
    @PostMapping("/redefinir")
    public ResponseEntity<RedefinirSenhaResponse> redefinir(@Valid @RequestBody RedefinirSenhaRequest request) {
        LoginResult sessao = redefinicaoSenhaService.redefinirSenha(request.token(), request.novaSenha());

        return ResponseEntity.ok()
                .header(AutenticacaoPorToken.HEADER_TOKEN, sessao.token())
                .body(new RedefinirSenhaResponse("Senha redefinida com sucesso.", sessao.response()));
    }

}
