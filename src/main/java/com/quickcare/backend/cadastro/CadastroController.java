package com.quickcare.backend.cadastro;

import com.quickcare.backend.cadastro.dto.CadastroPacienteRequest;
import com.quickcare.backend.cadastro.dto.CadastroProfissionalRequest;
import com.quickcare.backend.cadastro.dto.CadastroResponse;
import com.quickcare.backend.documento.TipoDocumento;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/usuarios/cadastro")
@RequiredArgsConstructor
public class CadastroController {

    private final CadastroService cadastroService;

    @PostMapping(value = "/paciente", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CadastroResponse> cadastrarPaciente(
            @Valid @RequestPart("dados") CadastroPacienteRequest request,
            @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
            @RequestParam(value = "tiposDocumento", required = false) List<TipoDocumento> tiposDocumento
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cadastroService.cadastrarPaciente(request, documentos, tiposDocumento));
    }

    @PostMapping(value = "/profissional", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CadastroResponse> cadastrarProfissional(
            @Valid @RequestPart("dados") CadastroProfissionalRequest request,
            @RequestPart(value = "documentos", required = false) List<MultipartFile> documentos,
            @RequestParam(value = "tiposDocumento", required = false) List<TipoDocumento> tiposDocumento
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cadastroService.cadastrarProfissional(request, documentos, tiposDocumento));
    }

}
