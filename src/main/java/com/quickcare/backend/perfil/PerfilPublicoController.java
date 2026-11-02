package com.quickcare.backend.perfil;

import com.quickcare.backend.perfil.dto.PerfilPublicoPacienteResponse;
import com.quickcare.backend.perfil.dto.PerfilPublicoProfissionalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Perfil de outro usuário; o próprio perfil, para editar, fica em {@code /perfil}. */
@RestController
@RequiredArgsConstructor
public class PerfilPublicoController {

    private final PerfilPublicoService perfilPublicoService;

    @GetMapping("/profissionais/{id}/perfil")
    public PerfilPublicoProfissionalResponse buscarProfissional(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return perfilPublicoService.buscarProfissional(usuarioId, id);
    }

    @GetMapping("/pacientes/{id}/perfil")
    public PerfilPublicoPacienteResponse buscarPaciente(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return perfilPublicoService.buscarPaciente(usuarioId, id);
    }

}
