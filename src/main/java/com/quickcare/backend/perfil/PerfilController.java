package com.quickcare.backend.perfil;

import com.quickcare.backend.perfil.dto.AtualizarPerfilPacienteRequest;
import com.quickcare.backend.perfil.dto.AtualizarPerfilProfissionalRequest;
import com.quickcare.backend.perfil.dto.PerfilAtualizadoResponse;
import com.quickcare.backend.perfil.dto.PerfilPacienteResponse;
import com.quickcare.backend.perfil.dto.PerfilProfissionalResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Meu perfil", description = "Dados do próprio perfil de paciente ou de profissional.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private static final String MENSAGEM_ATUALIZADO = "Dados atualizados com sucesso.";

    private final PerfilService perfilService;

    @Operation(summary = "Busca o perfil de paciente do usuário logado")
    @GetMapping("/paciente")
    public PerfilPacienteResponse buscarPaciente(@AuthenticationPrincipal Long usuarioId) {
        return perfilService.buscarPaciente(usuarioId);
    }

    @Operation(summary = "Atualiza o perfil de paciente")
    @PutMapping(value = "/paciente", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PerfilAtualizadoResponse<PerfilPacienteResponse> atualizarPaciente(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestPart("dados") AtualizarPerfilPacienteRequest request,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar
    ) {
        return new PerfilAtualizadoResponse<>(MENSAGEM_ATUALIZADO, perfilService.atualizarPaciente(usuarioId, request, avatar));
    }

    @Operation(summary = "Busca o perfil de profissional do usuário logado")
    @GetMapping("/profissional")
    public PerfilProfissionalResponse buscarProfissional(@AuthenticationPrincipal Long usuarioId) {
        return perfilService.buscarProfissional(usuarioId);
    }

    @Operation(summary = "Atualiza o perfil de profissional")
    @PutMapping(value = "/profissional", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PerfilAtualizadoResponse<PerfilProfissionalResponse> atualizarProfissional(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestPart("dados") AtualizarPerfilProfissionalRequest request,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar
    ) {
        return new PerfilAtualizadoResponse<>(MENSAGEM_ATUALIZADO, perfilService.atualizarProfissional(usuarioId, request, avatar));
    }

}
