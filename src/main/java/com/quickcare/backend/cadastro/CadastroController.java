package com.quickcare.backend.cadastro;

import com.quickcare.backend.cadastro.dto.CadastroPacienteRequest;
import com.quickcare.backend.cadastro.dto.CadastroProfissionalRequest;
import com.quickcare.backend.cadastro.dto.CadastroResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios/cadastro")
@RequiredArgsConstructor
public class CadastroController {

    private final CadastroService cadastroService;

    @PostMapping("/paciente")
    public ResponseEntity<CadastroResponse> cadastrarPaciente(@Valid @RequestBody CadastroPacienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cadastroService.cadastrarPaciente(request));
    }

    @PostMapping("/profissional")
    public ResponseEntity<CadastroResponse> cadastrarProfissional(@Valid @RequestBody CadastroProfissionalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cadastroService.cadastrarProfissional(request));
    }

}
