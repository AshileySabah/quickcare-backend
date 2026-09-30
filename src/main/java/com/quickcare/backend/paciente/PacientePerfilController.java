package com.quickcare.backend.paciente;

import com.quickcare.backend.paciente.dto.AtualizarPerfilPacienteRequest;
import com.quickcare.backend.paciente.dto.PerfilPacienteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/paciente/perfil")
@RequiredArgsConstructor
public class PacientePerfilController {

    private final PacientePerfilService pacientePerfilService;

    @GetMapping
    public PerfilPacienteResponse buscar(@AuthenticationPrincipal Long usuarioId) {
        return pacientePerfilService.buscar(usuarioId);
    }

    @PutMapping
    public PerfilPacienteResponse atualizar(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestBody AtualizarPerfilPacienteRequest request
    ) {
        return pacientePerfilService.atualizar(usuarioId, request);
    }
}