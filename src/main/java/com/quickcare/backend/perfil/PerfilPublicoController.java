package com.quickcare.backend.perfil;

import com.quickcare.backend.perfil.dto.PerfilPublicoPacienteResponse;
import com.quickcare.backend.perfil.dto.PerfilPublicoProfissionalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Perfis públicos", description = "Perfil de outra pessoa, respeitando bloqueios e o que pode ser revelado.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class PerfilPublicoController {

    private final PerfilPublicoService perfilPublicoService;

    @Operation(summary = "Mostra o perfil de um profissional")
    @GetMapping("/profissionais/{id}/perfil")
    public PerfilPublicoProfissionalResponse buscarProfissional(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return perfilPublicoService.buscarProfissional(usuarioId, id);
    }

    @Operation(summary = "Mostra o perfil de um paciente (só com proposta aceita)")
    @GetMapping("/pacientes/{id}/perfil")
    public PerfilPublicoPacienteResponse buscarPaciente(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return perfilPublicoService.buscarPaciente(usuarioId, id);
    }

}
