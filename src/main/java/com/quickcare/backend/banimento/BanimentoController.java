package com.quickcare.backend.banimento;

import com.quickcare.backend.banimento.dto.SituacaoBanimentoResponse;
import com.quickcare.backend.ocorrencia.dto.RecursoRequest;
import com.quickcare.backend.usuario.TipoPerfil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Banimento", description = "Situação de banimento do perfil e recurso do banido.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/banimentos")
@RequiredArgsConstructor
public class BanimentoController {

    private final BanimentoService banimentoService;

    @Operation(summary = "Mostra se o perfil está banido, o motivo e até quando")
    @GetMapping
    public SituacaoBanimentoResponse situacao(@AuthenticationPrincipal Long usuarioId, @RequestParam TipoPerfil perfil) {
        return banimentoService.situacao(usuarioId, perfil);
    }

    @Operation(summary = "Recorre do banimento (uma única vez)")
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
