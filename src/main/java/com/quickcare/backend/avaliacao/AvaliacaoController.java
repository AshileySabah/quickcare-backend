package com.quickcare.backend.avaliacao;

import com.quickcare.backend.avaliacao.dto.AvaliacaoRequest;
import com.quickcare.backend.avaliacao.dto.AvaliacoesPendentesResponse;
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

@Tag(name = "Avaliações", description = "Avaliação anônima e às cegas entre paciente e profissional depois do atendimento.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/avaliacoes")
@RequiredArgsConstructor
public class AvaliacaoController {

    private final AvaliacaoService avaliacaoService;

    @Operation(summary = "Lista os atendimentos que o perfil ainda pode avaliar")
    @GetMapping("/pendentes")
    public AvaliacoesPendentesResponse pendentes(@AuthenticationPrincipal Long usuarioId, @RequestParam TipoPerfil perfil) {
        return avaliacaoService.pendentes(usuarioId, perfil);
    }

    @Operation(summary = "Avalia um atendimento com estrelas e destaques")
    @PostMapping("/atendimentos/{id}")
    public AvaliacoesPendentesResponse avaliar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody AvaliacaoRequest request
    ) {
        return avaliacaoService.avaliar(usuarioId, id, request);
    }

}
