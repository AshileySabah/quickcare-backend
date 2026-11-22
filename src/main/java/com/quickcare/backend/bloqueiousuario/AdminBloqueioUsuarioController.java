package com.quickcare.backend.bloqueiousuario;

import com.quickcare.backend.administrador.dto.JustificativaRequest;
import com.quickcare.backend.bloqueiousuario.dto.BloqueioUsuarioAdminResponse;
import com.quickcare.backend.bloqueiousuario.dto.CriarBloqueioUsuarioRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin · Bloqueios entre usuários", description = "Bloqueios entre paciente e profissional vistos e aplicados pelo administrador.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/bloqueios-usuario")
@RequiredArgsConstructor
public class AdminBloqueioUsuarioController {

    private final AdminBloqueioUsuarioService adminBloqueioUsuarioService;

    @Operation(summary = "Lista os bloqueios entre usuários")
    @GetMapping
    public List<BloqueioUsuarioAdminResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam(required = false) String busca
    ) {
        return adminBloqueioUsuarioService.listar(usuarioId, busca);
    }

    @Operation(summary = "Bloqueia um paciente e um profissional entre si")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueioUsuarioAdminResponse bloquear(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestBody CriarBloqueioUsuarioRequest request
    ) {
        return adminBloqueioUsuarioService.bloquear(usuarioId, request.pacienteUsuarioId(), request.profissionalUsuarioId(),
                request.justificativa());
    }

    @Operation(summary = "Remove um bloqueio entre usuários")
    @PatchMapping("/{id}/remover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody JustificativaRequest request
    ) {
        adminBloqueioUsuarioService.remover(usuarioId, id, request.justificativa());
    }

}
