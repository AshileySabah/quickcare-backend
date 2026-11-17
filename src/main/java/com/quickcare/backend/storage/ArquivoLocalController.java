package com.quickcare.backend.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "quickcare.storage.tipo", havingValue = "local")
public class ArquivoLocalController {

    private final LocalArmazenamento armazenamento;

    @GetMapping(LocalArmazenamento.ROTA + "{pasta}/{arquivo}")
    public ResponseEntity<Resource> abrir(
            @PathVariable String pasta,
            @PathVariable String arquivo,
            @RequestParam long expira,
            @RequestParam String assinatura) {
        String chave = pasta + "/" + arquivo;
        if (!armazenamento.assinaturaValida(chave, expira, assinatura)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Path caminho;
        try {
            caminho = armazenamento.caminho(chave);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
        if (!Files.isRegularFile(caminho)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(arquivo).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(new FileSystemResource(caminho));
    }
}
