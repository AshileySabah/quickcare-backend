package com.quickcare.backend.atuacao;

import com.quickcare.backend.atuacao.dto.AlterarEspecialidadesRequest;
import com.quickcare.backend.atuacao.dto.AtuacaoRequest;
import com.quickcare.backend.atuacao.dto.AtuacaoResponse;
import com.quickcare.backend.atuacao.dto.ImpactoAtuacaoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/perfil/profissional/atuacoes")
@RequiredArgsConstructor
public class AtuacaoController {

    private final AtuacaoService atuacaoService;

    @GetMapping
    public List<AtuacaoResponse> listar(@AuthenticationPrincipal Long usuarioId) {
        return atuacaoService.listar(usuarioId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<AtuacaoResponse> incluir(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestPart("dados") AtuacaoRequest request,
            @RequestPart(value = "documento", required = false) MultipartFile documento
    ) {
        return atuacaoService.incluir(usuarioId, request, documento);
    }

    @GetMapping("/{id}/impacto")
    public ImpactoAtuacaoResponse impacto(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @RequestParam(required = false) List<Long> especialidadeIds
    ) {
        return atuacaoService.impacto(usuarioId, id, especialidadeIds);
    }

    @PutMapping("/{id}/especialidades")
    public List<AtuacaoResponse> alterarEspecialidades(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody AlterarEspecialidadesRequest request
    ) {
        return atuacaoService.alterarEspecialidades(usuarioId, id, request);
    }

    @DeleteMapping("/{id}")
    public List<AtuacaoResponse> remover(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return atuacaoService.remover(usuarioId, id);
    }

}
