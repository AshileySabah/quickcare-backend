package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.ocorrencia.dto.RecursoRequest;
import com.quickcare.backend.ocorrencia.dto.ResumoOcorrenciasResponse;
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
@RequestMapping("/ocorrencias")
@RequiredArgsConstructor
public class OcorrenciaController {

    private final OcorrenciaService ocorrenciaService;

    @GetMapping
    public ResumoOcorrenciasResponse resumo(@AuthenticationPrincipal Long usuarioId, @RequestParam TipoPerfil perfil) {
        return ocorrenciaService.resumo(usuarioId, perfil);
    }

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
