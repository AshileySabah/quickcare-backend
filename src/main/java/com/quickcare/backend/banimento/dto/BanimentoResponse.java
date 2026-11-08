package com.quickcare.backend.banimento.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.banimento.Banimento;
import com.quickcare.backend.banimento.TipoBanimento;
import com.quickcare.backend.usuario.TipoPerfil;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BanimentoResponse(
        Long id,
        TipoPerfil perfil,
        TipoBanimento tipo,
        Instant inicio,
        Instant fim,
        String justificativa,
        String aplicadoPor,
        Long denunciaId,
        boolean ativo,
        boolean podeRecorrer,
        Instant encerradoEm,
        Banimento.Encerramento encerramento,
        String encerradoPor,
        String justificativaEncerramento,
        Recurso recurso
) {

    public record Recurso(
            Banimento.StatusRecurso status,
            String texto,
            Instant criadoEm,
            Instant decididoEm,
            String decididoPor,
            String justificativa
    ) {
    }

    public static BanimentoResponse paraOUsuario(Banimento banimento, Instant agora) {
        return de(banimento, agora, false);
    }

    public static BanimentoResponse paraOAdmin(Banimento banimento, Instant agora) {
        return de(banimento, agora, true);
    }

    private static BanimentoResponse de(Banimento banimento, Instant agora, boolean admin) {
        return new BanimentoResponse(
                banimento.getId(),
                banimento.getPerfil(),
                banimento.getTipo(),
                banimento.getInicio(),
                banimento.getFim(),
                banimento.getJustificativa(),
                admin && banimento.getAplicadoPor() != null ? banimento.getAplicadoPor().getNome() : null,
                admin && banimento.getDenuncia() != null ? banimento.getDenuncia().getId() : null,
                banimento.ativo(agora),
                banimento.podeRecorrer(agora),
                banimento.getEncerradoEm(),
                banimento.getEncerramento(),
                admin && banimento.getEncerradoPor() != null ? banimento.getEncerradoPor().getNome() : null,
                banimento.getJustificativaEncerramento(),
                banimento.getRecursoStatus() == null ? null : new Recurso(
                        banimento.getRecursoStatus(),
                        banimento.getRecursoTexto(),
                        banimento.getRecursoCriadoEm(),
                        banimento.getRecursoDecididoEm(),
                        admin && banimento.getRecursoDecididoPor() != null ? banimento.getRecursoDecididoPor().getNome() : null,
                        banimento.getRecursoJustificativa()));
    }

}
