package com.quickcare.backend.validacao;

import com.quickcare.backend.validacao.dto.ReenvioEtapaRequest;
import com.quickcare.backend.validacao.dto.ValidacaoDetalheResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Validação cadastral", description = "Acompanhamento e reenvio das etapas do próprio cadastro.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/validacao")
@RequiredArgsConstructor
public class ValidacaoController {

    private final ValidacaoService validacaoService;

    @Operation(summary = "Mostra a validação do próprio perfil")
    @GetMapping("/{perfil}")
    public ValidacaoDetalheResponse minhaValidacao(@AuthenticationPrincipal Long usuarioId, @PathVariable String perfil) {
        return validacaoService.minhaValidacao(usuarioId, PerfilNaUrl.ler(perfil));
    }

    @Operation(summary = "Baixa um arquivo enviado em uma etapa")
    @GetMapping("/{perfil}/etapas/{etapaId}/{arquivo}")
    public ResponseEntity<byte[]> arquivo(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable String perfil,
            @PathVariable Long etapaId,
            @PathVariable String arquivo
    ) {
        return validacaoService.meuArquivo(usuarioId, PerfilNaUrl.ler(perfil), etapaId, ArquivoDaEtapa.ler(arquivo)).resposta();
    }

    @Operation(summary = "Reenvia uma etapa com dados ou documento novos")
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
