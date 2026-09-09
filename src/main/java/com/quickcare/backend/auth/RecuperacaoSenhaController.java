package com.quickcare.backend.auth;

import com.quickcare.backend.auth.dto.RedefinirSenhaRequest;
import com.quickcare.backend.auth.dto.SolicitarRecuperacaoRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class RecuperacaoSenhaController {

    private final RecuperacaoSenhaService recuperacaoSenhaService;

    @PostMapping("/esqueci-senha")
    public ResponseEntity<Map<String, String>> esqueciSenha(@Valid @RequestBody SolicitarRecuperacaoRequest request) {
        recuperacaoSenhaService.solicitarRecuperacao(request);
        return ResponseEntity.ok(Map.of("mensagem", "Se o e-mail existir em nossa base, um código foi enviado."));
    }

    @PostMapping("/redefinir-senha")
    public ResponseEntity<Map<String, String>> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequest request) {
        recuperacaoSenhaService.redefinirSenha(request);
        return ResponseEntity.ok(Map.of("mensagem", "Senha redefinida com sucesso."));
    }
}