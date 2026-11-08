package com.quickcare.backend.consulta;

import com.quickcare.backend.consulta.dto.ProfissionalPublicoResponse;
import com.quickcare.backend.consulta.dto.SolicitacaoPublicaResponse;
import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.perfil.PerfilPublicoService;
import com.quickcare.backend.perfil.dto.PerfilPublicoProfissionalResponse;
import com.quickcare.backend.solicitacao.ModalidadeSolicitacao;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/publico")
@RequiredArgsConstructor
public class ConsultaPublicaController {

    private final ConsultaPublicaService consultaPublicaService;
    private final PerfilPublicoService perfilPublicoService;

    @GetMapping("/profissionais")
    public List<ProfissionalPublicoResponse> profissionais(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam(required = false) CategoriaProfissional categoria,
            @RequestParam(required = false) Long especialidadeId,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) ModalidadeSolicitacao modalidade,
            @RequestParam(required = false) Double notaMinima
    ) {
        return consultaPublicaService.profissionais(usuarioId, new ConsultaPublicaService.FiltroProfissionais(
                categoria, especialidadeId, vazioComoNulo(cidade), vazioComoNulo(uf), modalidade, notaMinima));
    }

    @GetMapping("/profissionais/{id}")
    public PerfilPublicoProfissionalResponse profissional(@AuthenticationPrincipal Long usuarioId, @PathVariable Long id) {
        return perfilPublicoService.buscarProfissional(usuarioId, id);
    }

    @GetMapping("/solicitacoes")
    public List<SolicitacaoPublicaResponse> solicitacoes(
            @AuthenticationPrincipal Long usuarioId,
            @RequestParam(required = false) CategoriaProfissional categoria,
            @RequestParam(required = false) Long especialidadeId,
            @RequestParam(required = false) String cidade,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) ModalidadeSolicitacao modalidade
    ) {
        return consultaPublicaService.solicitacoes(usuarioId, new ConsultaPublicaService.FiltroSolicitacoes(
                categoria, especialidadeId, vazioComoNulo(cidade), vazioComoNulo(uf), modalidade));
    }

    private static String vazioComoNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

}
