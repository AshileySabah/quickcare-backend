package com.quickcare.backend.seguranca;

import com.quickcare.backend.banimento.Banimento;
import com.quickcare.backend.banimento.BanimentoRepository;
import com.quickcare.backend.ocorrencia.BloqueioRepository;
import com.quickcare.backend.ocorrencia.OcorrenciaService;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PendenciasDoPerfil {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BanimentoRepository banimentoRepository;
    private final BloqueioRepository bloqueioRepository;
    private final OcorrenciaService ocorrenciaService;

    public void prorrogar(Usuario usuario, TipoPerfil perfil, Instant congeladoEm, Duration duracao) {
        if (duracao.isNegative() || duracao.isZero()) {
            return;
        }
        banimentoRepository.findByUsuarioIdAndPerfilOrderByInicioDescIdDesc(usuario.getId(), perfil)
                .forEach(banimento -> banimento.prorrogarPorCongelamento(congeladoEm, duracao));
        bloqueioRepository.findByUsuarioIdAndPerfilOrderByInicioDesc(usuario.getId(), perfil)
                .forEach(bloqueio -> bloqueio.prorrogarPorCongelamento(congeladoEm, duracao));
    }

    public boolean banido(Usuario usuario, TipoPerfil perfil, Instant agora) {
        return banimentoRepository.findAtivo(usuario.getId(), perfil, agora).isPresent();
    }

    public List<String> descrever(Usuario usuario, TipoPerfil perfil, Instant agora) {
        List<String> pendencias = new ArrayList<>();
        Optional<Banimento> banimento = banimentoRepository.findAtivo(usuario.getId(), perfil, agora);
        banimento.ifPresent(ativo -> pendencias.add(ativo.getFim() == null
                ? "banimento permanente"
                : "banimento até " + DIA.format(ativo.getFim().atZone(FUSO))));
        ocorrenciaService.bloqueioAtivo(usuario.getId(), perfil)
                .ifPresent(bloqueio -> pendencias.add((perfil == TipoPerfil.PACIENTE
                        ? "bloqueio para criar solicitações"
                        : "bloqueio para enviar propostas")
                        + " até " + DIA.format(bloqueio.getFim().atZone(FUSO))));
        return pendencias;
    }

}
