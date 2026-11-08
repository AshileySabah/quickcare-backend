package com.quickcare.backend.denuncia;

import com.quickcare.backend.denuncia.dto.DenunciaEnviadaResponse;
import com.quickcare.backend.denuncia.dto.DenunciaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/denuncias")
@RequiredArgsConstructor
public class DenunciaController {

    private final DenunciaService denunciaService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DenunciaEnviadaResponse denunciar(
            @AuthenticationPrincipal Long usuarioId,
            @Valid @RequestPart("dados") DenunciaRequest request,
            @RequestPart(value = "anexo", required = false) MultipartFile anexo
    ) {
        return denunciaService.denunciar(usuarioId, request, anexo);
    }

}
