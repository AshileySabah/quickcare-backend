package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.administrador.dto.JustificativaRequest;
import com.quickcare.backend.ocorrencia.dto.DecisaoRecursoRequest;
import com.quickcare.backend.ocorrencia.dto.PerfilAdminDetalheResponse;
import com.quickcare.backend.ocorrencia.dto.PerfilAdminResumoResponse;
import com.quickcare.backend.usuario.TipoPerfil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Ocorrências, recursos e bloqueios dos perfis, no admin. */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPerfilController {

    private final AdminPerfilService adminPerfilService;

    @GetMapping("/perfis")
    public List<PerfilAdminResumoResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam(required = false) TipoPerfil perfil,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) AdminPerfilService.Situacao situacao
    ) {
        return adminPerfilService.listar(usuarioId, perfil, busca, situacao);
    }

    @GetMapping("/perfis/{perfil}/{id}")
    public PerfilAdminDetalheResponse detalhar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable TipoPerfil perfil,
            @PathVariable Long id
    ) {
        return adminPerfilService.detalhar(usuarioId, perfil, id);
    }

    @PostMapping("/perfis/{perfil}/{id}/ocorrencias")
    public PerfilAdminDetalheResponse lancarOcorrencia(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable TipoPerfil perfil,
            @PathVariable Long id,
            @Valid @RequestBody JustificativaRequest request
    ) {
        return adminPerfilService.lancarOcorrencia(usuarioId, perfil, id, request.justificativa());
    }

    @PatchMapping("/ocorrencias/{id}/anular")
    public PerfilAdminDetalheResponse anularOcorrencia(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody JustificativaRequest request
    ) {
        return adminPerfilService.anularOcorrencia(usuarioId, id, request.justificativa());
    }

    @PatchMapping("/bloqueios/{id}/remover")
    public PerfilAdminDetalheResponse removerBloqueio(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody JustificativaRequest request
    ) {
        return adminPerfilService.removerBloqueio(usuarioId, id, request.justificativa());
    }

    @PatchMapping("/recursos/{id}/decisao")
    public PerfilAdminDetalheResponse decidirRecurso(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody DecisaoRecursoRequest request
    ) {
        return adminPerfilService.decidirRecurso(usuarioId, id, request.anular(), request.justificativa());
    }

    /** Para o contador no menu do admin. */
    @GetMapping("/recursos/em-aberto")
    public Map<String, Long> recursosEmAberto(@AuthenticationPrincipal Long usuarioId) {
        return Map.of("total", adminPerfilService.contarRecursosEmAnalise(usuarioId));
    }

}
