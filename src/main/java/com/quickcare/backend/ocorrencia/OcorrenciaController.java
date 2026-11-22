package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.ocorrencia.dto.RecursoRequest;
import com.quickcare.backend.ocorrencia.dto.ResumoOcorrenciasResponse;
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

@Tag(name = "Ocorrências", description = "Ocorrências do perfil, contagem para bloqueio e recurso.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/ocorrencias")
@RequiredArgsConstructor
public class OcorrenciaController {

    private final OcorrenciaService ocorrenciaService;

    @Operation(summary = "Resume as ocorrências e o bloqueio atual do perfil")
    @GetMapping
    public ResumoOcorrenciasResponse resumo(@AuthenticationPrincipal Long usuarioId, @RequestParam TipoPerfil perfil) {
        return ocorrenciaService.resumo(usuarioId, perfil);
    }

    @Operation(summary = "Recorre de uma ocorrência (uma única vez)")
    @PostMapping("/{id}/recurso")
    public ResumoOcorrenciasResponse recorrer(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @RequestParam TipoPerfil perfil,
            @Valid @RequestBody RecursoRequest request
    ) {
        return ocorrenciaService.recorrer(usuarioId, perfil, id, request.texto());
    }

}
