package com.quickcare.backend.localizacao;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/ufs")
public class UfController {

    @GetMapping
    public List<UfResponse> listar() {
        return Arrays.stream(Uf.values())
                .map(uf -> new UfResponse(uf.name(), uf.getNome()))
                .toList();
    }

}
