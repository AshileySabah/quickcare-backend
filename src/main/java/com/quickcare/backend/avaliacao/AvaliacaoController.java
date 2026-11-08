package com.quickcare.backend.avaliacao;

import com.quickcare.backend.avaliacao.dto.AvaliacaoRequest;
import com.quickcare.backend.avaliacao.dto.AvaliacoesPendentesResponse;
import com.quickcare.backend.usuario.TipoPerfil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/avaliacoes")
@RequiredArgsConstructor
public class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    @GetMapping("/pendentes")
    public AvaliacoesPendentesResponse pendentes(@AuthenticationPrincipal Long usuarioId, @RequestParam TipoPerfil perfil) {
        return avaliacaoService.pendentes(usuarioId, perfil);
    }

    @PostMapping("/atendimentos/{id}")
    public AvaliacoesPendentesResponse avaliar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody AvaliacaoRequest request
    ) {
        return avaliacaoService.avaliar(usuarioId, id, request);
    }

}
