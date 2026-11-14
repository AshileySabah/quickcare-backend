package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.LoginRequest;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.auth.dto.PerfilAtivoRequest;
import com.quickcare.backend.jwt.AutenticacaoPorToken;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult resultado = authService.login(request);

        return ResponseEntity.ok()
                .header(AutenticacaoPorToken.HEADER_TOKEN, resultado.token())
                .body(resultado.response());
    }

    @GetMapping("/me")
    public LoginResponse me(@AuthenticationPrincipal Long usuarioId) {
        return authService.sessaoAtual(usuarioId);
    }

    @PatchMapping("/perfil-ativo")
    public LoginResponse definirPerfilAtivo(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestBody PerfilAtivoRequest request
    ) {
        return authService.definirPerfilAtivo(usuarioId, request.perfil());
    }

}
