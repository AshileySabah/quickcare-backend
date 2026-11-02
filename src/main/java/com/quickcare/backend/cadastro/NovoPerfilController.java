package com.quickcare.backend.cadastro;

import com.quickcare.backend.auth.AuthService;
import com.quickcare.backend.auth.dto.LoginResponse;
import com.quickcare.backend.cadastro.dto.NovoPerfilPacienteRequest;
import com.quickcare.backend.cadastro.dto.NovoPerfilProfissionalRequest;
import com.quickcare.backend.documento.TipoDocumento;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/perfis")
@RequiredArgsConstructor
public class NovoPerfilController {

    private final CadastroService cadastroService;
    private final AuthService authService;

    @PostMapping(value = "/paciente", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LoginResponse> criarPaciente(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestPart("dados") NovoPerfilPacienteRequest request,
            @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
            @RequestParam(value = "tiposDocumento", required = false) List<TipoDocumento> tiposDocumento
    ) {
        cadastroService.criarPerfilPaciente(usuarioId, request, documentos, tiposDocumento);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.sessaoAtual(usuarioId));
    }

    @PostMapping(value = "/profissional", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LoginResponse> criarProfissional(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestPart("dados") NovoPerfilProfissionalRequest request,
            @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
            @RequestParam(value = "tiposDocumento", required = false) List<TipoDocumento> tiposDocumento
    ) {
        cadastroService.criarPerfilProfissional(usuarioId, request, documentos, tiposDocumento);
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.sessaoAtual(usuarioId));
    }

}
