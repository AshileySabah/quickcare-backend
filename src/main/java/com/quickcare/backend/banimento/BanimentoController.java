package com.quickcare.backend.banimento;

import com.quickcare.backend.banimento.dto.SituacaoBanimentoResponse;
import com.quickcare.backend.ocorrencia.dto.RecursoRequest;
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
@RequestMapping("/banimentos")
@RequiredArgsConstructor
public class BanimentoController {

    private final BanimentoService banimentoService;

    @GetMapping
    public SituacaoBanimentoResponse situacao(@AuthenticationPrincipal Long usuarioId, @RequestParam TipoPerfil perfil) {
        return banimentoService.situacao(usuarioId, perfil);
    }

    @PostMapping("/{id}/recurso")
    public SituacaoBanimentoResponse recorrer(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @RequestParam TipoPerfil perfil,
            @Valid @RequestBody RecursoRequest request
    ) {
        return banimentoService.recorrer(usuarioId, perfil, id, request.texto());
    }

}
