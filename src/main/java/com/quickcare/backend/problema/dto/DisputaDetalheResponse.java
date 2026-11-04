package com.quickcare.backend.problema.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.quickcare.backend.administrador.dto.AcaoAdminResponse;
import com.quickcare.backend.atendimento.AtendimentoEvento;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.atendimento.TipoEventoAtendimento;

import java.time.Instant;
import java.util.List;

public record DisputaDetalheResponse(
        DisputaResumoResponse resumo,
        StatusAtendimento statusAtendimento,
        StatusAtendimento statusAntesDoRelato,
        ProblemaResponse problema,
        List<Evento> eventos,
        List<AcaoAdminResponse> acoes
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Evento(TipoEventoAtendimento tipo, String autor, Instant inicio, Instant termino, String detalhe,
                         Instant criadoEm) {

        public static Evento de(AtendimentoEvento evento) {
            return new Evento(evento.getTipo(), evento.getAutor() == null ? null : evento.getAutor().getNome(),
                    evento.getInicio(), evento.getTermino(), evento.getDetalhe(), evento.getCriadoEm());
        }
    }

}
