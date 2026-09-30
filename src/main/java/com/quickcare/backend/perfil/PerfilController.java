package com.quickcare.backend.perfil;

import com.quickcare.backend.perfil.dto.AtualizarPerfilPacienteRequest;
import com.quickcare.backend.perfil.dto.AtualizarPerfilProfissionalRequest;
import com.quickcare.backend.perfil.dto.PerfilAtualizadoResponse;
import com.quickcare.backend.perfil.dto.PerfilPacienteResponse;
import com.quickcare.backend.perfil.dto.PerfilProfissionalResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private static final String MENSAGEM_ATUALIZADO = "Dados atualizados com sucesso.";

    private final PerfilService perfilService;

    @GetMapping("/paciente")
    public PerfilPacienteResponse buscarPaciente(@AuthenticationPrincipal Long usuarioId) {
        return perfilService.buscarPaciente(usuarioId);
    }

    @PutMapping(value = "/paciente", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PerfilAtualizadoResponse<PerfilPacienteResponse> atualizarPaciente(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestPart("dados") AtualizarPerfilPacienteRequest request,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar
    ) {
        return new PerfilAtualizadoResponse<>(MENSAGEM_ATUALIZADO, perfilService.atualizarPaciente(usuarioId, request, avatar));
    }

    @GetMapping("/profissional")
    public PerfilProfissionalResponse buscarProfissional(@AuthenticationPrincipal Long usuarioId) {
        return perfilService.buscarProfissional(usuarioId);
    }

    @PutMapping(value = "/profissional", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PerfilAtualizadoResponse<PerfilProfissionalResponse> atualizarProfissional(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestPart("dados") AtualizarPerfilProfissionalRequest request,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar
    ) {
        return new PerfilAtualizadoResponse<>(MENSAGEM_ATUALIZADO, perfilService.atualizarProfissional(usuarioId, request, avatar));
    }

}
