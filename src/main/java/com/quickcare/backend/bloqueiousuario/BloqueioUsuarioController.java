package com.quickcare.backend.bloqueiousuario;

import com.quickcare.backend.bloqueiousuario.dto.BloqueioUsuarioResponse;
import com.quickcare.backend.bloqueiousuario.dto.PreviaBloqueioResponse;
import com.quickcare.backend.usuario.TipoPerfil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/bloqueios")
@RequiredArgsConstructor
public class BloqueioUsuarioController {

    private final BloqueioUsuarioService bloqueioUsuarioService;

    @GetMapping
    public List<BloqueioUsuarioResponse> listar(@AuthenticationPrincipal Long usuarioId, @RequestParam TipoPerfil perfil) {
        return bloqueioUsuarioService.listar(usuarioId, perfil);
    }

    @GetMapping("/profissionais/{id}/previa")
    public PreviaBloqueioResponse previaDoProfissional(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return bloqueioUsuarioService.previaDoProfissional(usuarioId, id);
    }

    @PostMapping("/profissionais/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueioUsuarioResponse bloquearProfissional(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return bloqueioUsuarioService.bloquearProfissional(usuarioId, id);
    }

    @GetMapping("/pacientes/{id}/previa")
    public PreviaBloqueioResponse previaDoPaciente(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return bloqueioUsuarioService.previaDoPaciente(usuarioId, id);
    }

    @PostMapping("/pacientes/{id}")
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueioUsuarioResponse bloquearPaciente(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return bloqueioUsuarioService.bloquearPaciente(usuarioId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desbloquear(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        bloqueioUsuarioService.desbloquear(usuarioId, id);
    }

}
