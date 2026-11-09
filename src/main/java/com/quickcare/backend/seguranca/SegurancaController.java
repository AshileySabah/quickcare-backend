package com.quickcare.backend.seguranca;

import com.quickcare.backend.auth.AuthCookies;
import com.quickcare.backend.auth.LoginResult;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.common.MensagemResponse;
import com.quickcare.backend.seguranca.dto.CodigoEmailRequest;
import com.quickcare.backend.seguranca.dto.CodigoEncerramentoRequest;
import com.quickcare.backend.seguranca.dto.ConfirmacaoCodigoRequest;
import com.quickcare.backend.seguranca.dto.EncerramentoRequest;
import com.quickcare.backend.seguranca.dto.SegurancaResponse;
import com.quickcare.backend.seguranca.dto.TrocarSenhaRequest;
import com.quickcare.backend.usuario.TipoPerfil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/seguranca")
@RequiredArgsConstructor
public class SegurancaController {

    private final SegurancaService segurancaService;
    private final AuthCookies authCookies;

    @GetMapping
    public SegurancaResponse situacao(@AuthenticationPrincipal Long usuarioId) {
        return segurancaService.situacao(usuarioId);
    }

    @PostMapping("/email/codigo")
    public MensagemResponse pedirCodigoDeEmail(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestBody CodigoEmailRequest request
    ) {
        segurancaService.pedirCodigoDeEmail(usuarioId, request);
        return new MensagemResponse("Enviamos um código de 6 números para o novo e-mail.");
    }

    @PatchMapping("/email")
    public ResponseEntity<LoginResponse> confirmarEmail(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestBody ConfirmacaoCodigoRequest request
    ) {
        LoginResult sessao = segurancaService.confirmarEmail(usuarioId, request.codigo());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.accessToken(sessao.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, authCookies.refreshToken(sessao.refreshToken()).toString())
                .body(sessao.response());
    }

    @PatchMapping("/senha")
    public ResponseEntity<MensagemResponse> trocarSenha(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestBody TrocarSenhaRequest request
    ) {
        LoginResult sessao = segurancaService.trocarSenha(usuarioId, request);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookies.accessToken(sessao.accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, authCookies.refreshToken(sessao.refreshToken()).toString())
                .body(new MensagemResponse("Senha alterada. Os outros dispositivos foram desconectados."));
    }

    @PostMapping("/encerramento/codigo")
    public MensagemResponse pedirCodigoDeEncerramento(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestBody CodigoEncerramentoRequest request
    ) {
        segurancaService.pedirCodigoDeEncerramento(usuarioId, request);
        return new MensagemResponse("Enviamos um código de 6 números para o seu e-mail.");
    }

    @PostMapping("/encerramento")
    public ResponseEntity<LoginResponse> encerrar(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestBody EncerramentoRequest request
    ) {
        Optional<LoginResponse> sessao = segurancaService.encerrar(usuarioId, request);
        if (sessao.isPresent()) {
            return ResponseEntity.ok(sessao.get());
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, authCookies.expiredAccessToken().toString())
                .header(HttpHeaders.SET_COOKIE, authCookies.expiredRefreshToken().toString())
                .build();
    }

    @PostMapping("/perfis/recuperar")
    public LoginResponse recuperarPerfil(@AuthenticationPrincipal Long usuarioId, @RequestParam TipoPerfil perfil) {
        return segurancaService.recuperarPerfil(usuarioId, perfil);
    }

}
