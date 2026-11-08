package com.quickcare.backend.validacao;

import com.quickcare.backend.validacao.dto.ReenvioEtapaRequest;
import com.quickcare.backend.validacao.dto.ValidacaoDetalheResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/validacao")
@RequiredArgsConstructor
public class ValidacaoController {

    private final ValidacaoService validacaoService;

    @GetMapping("/{perfil}")
    public ValidacaoDetalheResponse minhaValidacao(@AuthenticationPrincipal Long usuarioId, @PathVariable String perfil) {
        return validacaoService.minhaValidacao(usuarioId, PerfilNaUrl.ler(perfil));
    }

    @GetMapping("/{perfil}/etapas/{etapaId}/{arquivo}")
    public ResponseEntity<byte[]> arquivo(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable String perfil,
            @PathVariable Long etapaId,
            @PathVariable String arquivo
    ) {
        return validacaoService.meuArquivo(usuarioId, PerfilNaUrl.ler(perfil), etapaId, ArquivoDaEtapa.ler(arquivo)).resposta();
    }

    @PostMapping(value = "/{perfil}/etapas/{tipo}/reenvio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ValidacaoDetalheResponse reenviar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable String perfil,
            @PathVariable TipoEtapaValidacao tipo,
            @RequestParam(required = false) Long atuacao,
            @Valid @RequestPart("dados") ReenvioEtapaRequest request,
            @RequestPart(value = "documento", required = false) MultipartFile documento
    ) {
        return validacaoService.reenviar(usuarioId, PerfilNaUrl.ler(perfil), tipo, atuacao, request, documento);
    }

}
