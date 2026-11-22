package com.quickcare.backend.localizacao;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@Tag(name = "Catálogos", description = "Listas usadas nos formulários.")
@RestController
@RequestMapping("/ufs")
public class UfController {

    @Operation(summary = "Lista as UFs")
    @GetMapping
    public List<UfResponse> listar() {
        return Arrays.stream(Uf.values())
                .map(uf -> new UfResponse(uf.name(), uf.getNome()))
                .toList();
    }

}
