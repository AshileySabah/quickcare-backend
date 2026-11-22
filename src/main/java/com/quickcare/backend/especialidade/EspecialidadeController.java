package com.quickcare.backend.especialidade;

import com.quickcare.backend.especialidade.dto.CategoriaProfissionalResponse;
import com.quickcare.backend.especialidade.dto.EspecialidadeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@Tag(name = "Catálogos", description = "Listas usadas nos formulários.")
@RestController
@RequestMapping("/especialidades")
@RequiredArgsConstructor
public class EspecialidadeController {

    private final EspecialidadeRepository especialidadeRepository;

    @Operation(summary = "Lista as especialidades por categoria")
    @GetMapping
    public List<EspecialidadeResponse> listar() {
        return especialidadeRepository.findAll().stream()
                .map(especialidade -> new EspecialidadeResponse(
                        especialidade.getId(),
                        especialidade.getNome(),
                        especialidade.getCategoria()
                ))
                .toList();
    }

    @Operation(summary = "Lista as categorias profissionais")
    @GetMapping("/categorias")
    public List<CategoriaProfissionalResponse> categorias() {
        return Arrays.stream(CategoriaProfissional.values())
                .map(categoria -> new CategoriaProfissionalResponse(categoria, categoria.getRotulo(), categoria.getSiglaRegistro()))
                .toList();
    }

}
