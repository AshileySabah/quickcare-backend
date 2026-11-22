package com.quickcare.backend.bloqueiousuario;

import com.quickcare.backend.bloqueiousuario.dto.BloquearRequest;
import com.quickcare.backend.bloqueiousuario.dto.BloqueioUsuarioResponse;
import com.quickcare.backend.bloqueiousuario.dto.PreviaBloqueioResponse;
import com.quickcare.backend.usuario.TipoPerfil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Bloqueio entre usuários", description = "Paciente bloqueia profissional e vice-versa; o bloqueio vale para os dois lados.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/bloqueios")
@RequiredArgsConstructor
public class BloqueioUsuarioController {

    private final BloqueioUsuarioService bloqueioUsuarioService;

    @Operation(summary = "Lista as pessoas que o perfil bloqueou")
    @GetMapping
    public List<BloqueioUsuarioResponse> listar(@AuthenticationPrincipal Long usuarioId, @RequestParam TipoPerfil perfil) {
        return bloqueioUsuarioService.listar(usuarioId, perfil);
    }

    @Operation(summary = "Mostra o que será encerrado ao bloquear o profissional")
    @GetMapping("/profissionais/{id}/previa")
    public PreviaBloqueioResponse previaDoProfissional(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return bloqueioUsuarioService.previaDoProfissional(usuarioId, id);
    }

    @Operation(summary = "Paciente bloqueia um profissional")
    @PostMapping("/profissionais/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueioUsuarioResponse bloquearProfissional(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id,
                                                        @Valid @RequestBody(required = false) BloquearRequest request) {
        return bloqueioUsuarioService.bloquearProfissional(usuarioId, id, BloquearRequest.motivoDe(request));
    }

    @Operation(summary = "Mostra o que será encerrado ao bloquear o paciente")
    @GetMapping("/pacientes/{id}/previa")
    public PreviaBloqueioResponse previaDoPaciente(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return bloqueioUsuarioService.previaDoPaciente(usuarioId, id);
    }

    @Operation(summary = "Profissional bloqueia um paciente")
    @PostMapping("/pacientes/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueioUsuarioResponse bloquearPaciente(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id,
                                                    @Valid @RequestBody(required = false) BloquearRequest request) {
        return bloqueioUsuarioService.bloquearPaciente(usuarioId, id, BloquearRequest.motivoDe(request));
    }

    @Operation(summary = "Desfaz um bloqueio")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desbloquear(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        bloqueioUsuarioService.desbloquear(usuarioId, id);
    }

}
