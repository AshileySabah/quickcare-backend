package com.quickcare.backend.problema;

import com.quickcare.backend.atendimento.dto.AtendimentoResponse;
import com.quickcare.backend.problema.dto.ContestacaoRequest;
import com.quickcare.backend.problema.dto.RelatoRequest;
import com.quickcare.backend.problema.dto.UrlResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/atendimentos/{id}/problema")
@RequiredArgsConstructor
public class ProblemaController {

    private final ProblemaService problemaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AtendimentoResponse relatar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestPart("dados") RelatoRequest request,
            @RequestPart(value = "anexos", required = false) List<MultipartFile> anexos
    ) {
        return problemaService.relatar(usuarioId, id, request, anexos);
    }

    @PatchMapping("/confirmar")
    public AtendimentoResponse confirmar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return problemaService.confirmar(usuarioId, id);
    }

    @PostMapping(value = "/contestacao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AtendimentoResponse contestar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestPart("dados") ContestacaoRequest request,
            @RequestPart(value = "anexos", required = false) List<MultipartFile> anexos
    ) {
        return problemaService.contestar(usuarioId, id, request, anexos);
    }

    @GetMapping("/anexos/{anexoId}")
    public UrlResponse anexo(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id, @PathVariable Long anexoId) {
        return new UrlResponse(problemaService.urlDoAnexo(usuarioId, id, anexoId));
    }

}
