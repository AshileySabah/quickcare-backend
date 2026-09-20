package com.quickcare.backend.especialidade;

import com.quickcare.backend.especialidade.dto.EspecialidadeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/especialidades")
@RequiredArgsConstructor
public class EspecialidadeController {

    private final EspecialidadeRepository especialidadeRepository;

    @GetMapping
    public List<EspecialidadeResponse> listar() {
        return especialidadeRepository.findAll().stream()
                .map(especialidade -> new EspecialidadeResponse(especialidade.getId(), especialidade.getNome()))
                .toList();
    }

}
