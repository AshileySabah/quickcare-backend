package com.quickcare.backend.validacao;

import com.quickcare.backend.validacao.dto.DecisaoEtapaRequest;
import com.quickcare.backend.validacao.dto.PreValidacaoResponse;
import com.quickcare.backend.validacao.dto.ValidacaoDetalheResponse;
import com.quickcare.backend.validacao.dto.ValidacaoResumoResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/admin/validacoes")
@RequiredArgsConstructor
public class AdminValidacaoController {

    private final ValidacaoService validacaoService;
    private final PreValidacaoService preValidacaoService;

    @GetMapping("/{perfil}")
    public List<ValidacaoResumoResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable String perfil,
            @RequestParam(required = false) StatusValidacao status,
            @RequestParam(defaultValue = "false") boolean emRevalidacao
    ) {
        return validacaoService.listar(usuarioId, PerfilNaUrl.ler(perfil), status, emRevalidacao);
    }

    @GetMapping("/{perfil}/{id}")
    public ValidacaoDetalheResponse detalhar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable String perfil,
            @PathVariable Long id
    ) {
        return validacaoService.detalhar(usuarioId, PerfilNaUrl.ler(perfil), id);
    }

    @PostMapping(value = "/{perfil}/{id}/etapas/{tipo}/decisao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ValidacaoDetalheResponse decidir(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable String perfil,
            @PathVariable Long id,
            @PathVariable TipoEtapaValidacao tipo,
            @Valid @RequestPart("dados") DecisaoEtapaRequest request,
            @RequestPart(value = "anexo", required = false) MultipartFile anexo
    ) {
        return validacaoService.decidir(usuarioId, PerfilNaUrl.ler(perfil), id, tipo, request, anexo);
    }

    @PostMapping("/{perfil}/{id}/etapas/{tipo}/pre-validacao")
    public PreValidacaoResponse preValidar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable String perfil,
            @PathVariable Long id,
            @PathVariable TipoEtapaValidacao tipo
    ) {
        return preValidacaoService.preValidar(usuarioId, PerfilNaUrl.ler(perfil), id, tipo);
    }

}
