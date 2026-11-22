package com.quickcare.backend.denuncia;

import com.quickcare.backend.denuncia.dto.DecisaoDenunciaRequest;
import com.quickcare.backend.denuncia.dto.DenunciaDetalheResponse;
import com.quickcare.backend.denuncia.dto.DenunciaResumoResponse;
import com.quickcare.backend.problema.dto.UrlResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Admin · Denúncias", description = "Fila e julgamento das denúncias.")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/admin/denuncias")
@RequiredArgsConstructor
public class AdminDenunciaController {

    private final DenunciaService denunciaService;

    @Operation(summary = "Lista as denúncias por situação")
    @GetMapping
    public List<DenunciaResumoResponse> listar(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam(required = false) List<StatusDenuncia> status
    ) {
        return denunciaService.listar(usuarioId, status);
    }

    @Operation(summary = "Conta as denúncias em julgamento")
    @GetMapping("/em-aberto")
    public Map<String, Long> emAberto(@AuthenticationPrincipal Long usuarioId) {
        return Map.of("total", denunciaService.contarAbertas(usuarioId));
    }

    @Operation(summary = "Detalha uma denúncia")
    @GetMapping("/{id}")
    public DenunciaDetalheResponse detalhar(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return denunciaService.detalhar(usuarioId, id);
    }

    @Operation(summary = "Julga a denúncia e escolhe a consequência")
    @PostMapping("/{id}/decisao")
    public DenunciaDetalheResponse julgar(
            @AuthenticationPrincipal Long usuarioId,
            @PathVariable Long id,
            @Valid @RequestBody DecisaoDenunciaRequest request
    ) {
        return denunciaService.julgar(usuarioId, id, request);
    }

    @Operation(summary = "Gera o link do anexo da denúncia")
    @GetMapping("/{id}/anexo")
    public UrlResponse anexo(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return new UrlResponse(denunciaService.urlDoAnexo(usuarioId, id));
    }

}
