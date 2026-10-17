package com.quickcare.backend.validacao;

import com.quickcare.backend.validacao.dto.ReenvioEtapaRequest;
import com.quickcare.backend.validacao.dto.ValidacaoDetalheResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Andamento da validação do próprio cadastro. `perfil` na URL: "paciente" ou "profissional". */
@RestController
@RequestMapping("/validacao")
@RequiredArgsConstructor
public class ValidacaoController {

    private final ValidacaoService validacaoService;

    @GetMapping("/{perfil}")
    public ValidacaoDetalheResponse minhaValidacao(@AuthenticationPrincipal Long usuarioId, @PathVariable String perfil) {
        return validacaoService.minhaValidacao(usuarioId, PerfilNaUrl.ler(perfil));
    }

    @PostMapping(value = "/{perfil}/etapas/{tipo}/reenvio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ValidacaoDetalheResponse reenviar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable String perfil,
            @PathVariable TipoEtapaValidacao tipo,
            @Valid @RequestPart("dados") ReenvioEtapaRequest request,
            @RequestPart("documento") MultipartFile documento
    ) {
        return validacaoService.reenviar(usuarioId, PerfilNaUrl.ler(perfil), tipo, request, documento);
    }

}
