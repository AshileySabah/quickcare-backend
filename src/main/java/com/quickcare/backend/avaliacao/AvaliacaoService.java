package com.quickcare.backend.avaliacao;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.atendimento.exception.AtendimentoNaoEncontradoException;
import com.quickcare.backend.avaliacao.dto.AvaliacaoRequest;
import com.quickcare.backend.avaliacao.dto.AvaliacoesPendentesResponse;
import com.quickcare.backend.avaliacao.dto.DestaqueResponse;
import com.quickcare.backend.avaliacao.dto.ResumoAvaliacoesResponse;
import com.quickcare.backend.bloqueiousuario.BloqueioUsuarioRepository;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacaoRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final BloqueioUsuarioRepository bloqueioUsuarioRepository;
    private final Notificador notificador;
    private final Clock clock;
    private final TransactionTemplate transacao;

    public AvaliacaoService(
            AvaliacaoRepository avaliacaoRepository,
            AtendimentoRepository atendimentoRepository,
            PacienteRepository pacienteRepository,
            ProfissionalRepository profissionalRepository,
            BloqueioUsuarioRepository bloqueioUsuarioRepository,
            Notificador notificador,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.avaliacaoRepository = avaliacaoRepository;
        this.atendimentoRepository = atendimentoRepository;
        this.pacienteRepository = pacienteRepository;
        this.profissionalRepository = profissionalRepository;
        this.bloqueioUsuarioRepository = bloqueioUsuarioRepository;
        this.notificador = notificador;
        this.clock = clock;
        this.transacao = new TransactionTemplate(transactionManager);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public AvaliacoesPendentesResponse pendentes(Long usuarioId, TipoPerfil perfil) {
        LadoAtendimento lado = ladoDoPerfil(perfil);
        Instant desde = Instant.now(clock).minus(RegrasAvaliacao.JANELA);
        List<Atendimento> atendimentos = perfil == TipoPerfil.PACIENTE
                ? pacienteRepository.findByUsuarioId(usuarioId)
                        .filter(paciente -> !paciente.isBanido())
                        .map(paciente -> avaliacaoRepository.pendentesDoPaciente(
                                paciente.getId(), lado, StatusAtendimento.REALIZADOS, desde))
                        .orElse(List.of())
                : profissionalRepository.findByUsuarioId(usuarioId)
                        .filter(profissional -> !profissional.isBanido())
                        .map(profissional -> avaliacaoRepository.pendentesDoProfissional(
                                profissional.getId(), lado, StatusAtendimento.REALIZADOS, desde))
                        .orElse(List.of());

        return new AvaliacoesPendentesResponse(
                atendimentos.stream().map(atendimento -> pendente(atendimento, lado)).toList(),
                DestaqueAvaliacao.paraAvaliar(outroLado(lado)).stream().map(DestaqueResponse::de).toList());
    }

    @Transactional
    public AvaliacoesPendentesResponse avaliar(Long usuarioId, Long atendimentoId, AvaliacaoRequest request) {
        Atendimento atendimento = atendimentoRepository.findWithPartesById(atendimentoId)
                .filter(encontrado -> encontrado.ladoDe(usuarioId).isPresent())
                .orElseThrow(AtendimentoNaoEncontradoException::new);
        LadoAtendimento lado = atendimento.ladoDe(usuarioId).orElseThrow(AtendimentoNaoEncontradoException::new);
        Paciente paciente = atendimento.getSolicitacao().getPaciente();
        Profissional profissional = atendimento.getProposta().getProfissional();

        if (lado == LadoAtendimento.PACIENTE ? paciente.isBanido() : profissional.isBanido()) {
            throw new PerfilNaoAutorizadoException("Seu perfil não pode avaliar atendimentos.");
        }
        exigirAvaliavel(atendimento, Instant.now(clock));
        exigir(!avaliacaoRepository.existsByAtendimentoIdAndAvaliador(atendimento.getId(), lado),
                "Você já avaliou este atendimento.");

        Set<DestaqueAvaliacao> destaques = request.destaques() == null || request.destaques().isEmpty()
                ? EnumSet.noneOf(DestaqueAvaliacao.class)
                : EnumSet.copyOf(request.destaques());
        LadoAtendimento avaliado = outroLado(lado);
        exigir(destaques.stream().allMatch(destaque -> destaque.serveParaAvaliar(avaliado)),
                "Escolha os destaques da lista.");

        avaliacaoRepository.save(Avaliacao.builder()
                .atendimento(atendimento)
                .avaliador(lado)
                .paciente(paciente)
                .profissional(profissional)
                .nota(request.nota())
                .destaques(destaques)
                .build());

        return pendentes(usuarioId, lado == LadoAtendimento.PACIENTE ? TipoPerfil.PACIENTE : TipoPerfil.PROFISSIONAL);
    }

    @Transactional(readOnly = true)
    public ResumoAvaliacoesResponse resumoDoProfissional(Long profissionalId) {
        Instant fimDaJanela = Instant.now(clock).minus(RegrasAvaliacao.JANELA);
        return resumo(
                avaliacaoRepository.resumoDoProfissional(profissionalId, StatusAtendimento.REALIZADOS, fimDaJanela),
                avaliacaoRepository.destaquesDoProfissional(profissionalId, StatusAtendimento.REALIZADOS, fimDaJanela));
    }

    @Transactional(readOnly = true)
    public ResumoAvaliacoesResponse resumoDoPaciente(Long pacienteId) {
        Instant fimDaJanela = Instant.now(clock).minus(RegrasAvaliacao.JANELA);
        return resumo(
                avaliacaoRepository.resumoDoPaciente(pacienteId, StatusAtendimento.REALIZADOS, fimDaJanela),
                avaliacaoRepository.destaquesDoPaciente(pacienteId, StatusAtendimento.REALIZADOS, fimDaJanela));
    }

    public void processarLembretes() {
        Instant agora = Instant.now(clock);
        for (Long id : avaliacaoRepository.findIdsComLembreteDeAvaliacaoDevido(
                StatusAtendimento.REALIZADOS,
                agora.minus(RegrasAvaliacao.JANELA),
                agora.minus(RegrasAvaliacao.PRIMEIRO_LEMBRETE),
                agora.minus(RegrasAvaliacao.SEGUNDO_LEMBRETE))) {
            try {
                transacao.executeWithoutResult(status -> lembrar(id, agora));
            } catch (RuntimeException ex) {
                log.warn("Não foi possível lembrar a avaliação do atendimento {}: {}", id, ex.getMessage());
            }
        }
    }

    private void lembrar(Long id, Instant agora) {
        Atendimento atendimento = atendimentoRepository.findWithPartesById(id).orElse(null);
        if (atendimento == null || !StatusAtendimento.REALIZADOS.contains(atendimento.getStatus())) {
            return;
        }

        boolean segundo = atendimento.getLembreteAvaliacao7dEm() == null
                && !agora.isBefore(atendimento.getTermino().plus(RegrasAvaliacao.SEGUNDO_LEMBRETE));
        boolean primeiro = atendimento.getLembreteAvaliacao3dEm() == null
                && !agora.isBefore(atendimento.getTermino().plus(RegrasAvaliacao.PRIMEIRO_LEMBRETE));
        if (!segundo && !primeiro) {
            return;
        }

        List<LadoAtendimento> jaAvaliaram = avaliacaoRepository.findAvaliadoresDoAtendimento(atendimento.getId());
        for (LadoAtendimento lado : List.of(LadoAtendimento.PACIENTE, LadoAtendimento.PROFISSIONAL)) {
            if (!jaAvaliaram.contains(lado)) {
                notificador.lembreteDeAvaliacao(atendimento, lado, RegrasAvaliacao.prazo(atendimento));
            }
        }

        atendimento.setLembreteAvaliacao3dEm(agora);
        if (segundo) {
            atendimento.setLembreteAvaliacao7dEm(agora);
        }
    }

    private AvaliacoesPendentesResponse.Pendente pendente(Atendimento atendimento, LadoAtendimento lado) {
        Paciente paciente = atendimento.getSolicitacao().getPaciente();
        Profissional profissional = atendimento.getProposta().getProfissional();
        boolean bloqueado = bloqueioUsuarioRepository.existsByPacienteIdAndProfissionalId(paciente.getId(), profissional.getId());
        String nome = lado == LadoAtendimento.PACIENTE
                ? profissional.getUsuario().getNome()
                : paciente.getUsuario().getNome();

        return new AvaliacoesPendentesResponse.Pendente(
                atendimento.getId(),
                atendimento.getSolicitacao().getId(),
                Notificador.servico(atendimento.getSolicitacao()),
                bloqueado ? null : nome,
                atendimento.getInicio(),
                atendimento.getTermino(),
                RegrasAvaliacao.prazo(atendimento));
    }

    private static ResumoAvaliacoesResponse resumo(AvaliacaoRepository.Resumo resumo,
                                                   List<AvaliacaoRepository.ContagemDestaque> contagens) {
        long total = resumo == null ? 0 : resumo.getTotal();
        Double media = total == 0 || resumo.getMedia() == null
                ? null
                : BigDecimal.valueOf(resumo.getMedia()).setScale(1, RoundingMode.HALF_UP).doubleValue();
        return new ResumoAvaliacoesResponse(media, total, contagens.stream()
                .limit(RegrasAvaliacao.DESTAQUES_NO_PERFIL)
                .map(contagem -> new ResumoAvaliacoesResponse.DestaqueRecebido(
                        contagem.getDestaque(),
                        contagem.getDestaque().getRotulo(),
                        contagem.getDestaque().isPositivo(),
                        contagem.getTotal()))
                .toList());
    }

    private static void exigirAvaliavel(Atendimento atendimento, Instant agora) {
        StatusAtendimento status = atendimento.getStatus();
        if (StatusAtendimento.EM_PROBLEMA.contains(status)) {
            throw new TransicaoStatusInvalidaException(
                    "Este atendimento tem um problema relatado. A avaliação volta a ficar disponível quando ele for resolvido.");
        }
        exigir(StatusAtendimento.REALIZADOS.contains(status),
                status.ativo() ? "A avaliação abre quando o atendimento terminar." : "Este atendimento não pode ser avaliado.");
        exigir(agora.isBefore(RegrasAvaliacao.prazo(atendimento)), "O prazo para avaliar este atendimento já acabou.");
    }

    private static LadoAtendimento ladoDoPerfil(TipoPerfil perfil) {
        return perfil == TipoPerfil.PACIENTE ? LadoAtendimento.PACIENTE : LadoAtendimento.PROFISSIONAL;
    }

    private static LadoAtendimento outroLado(LadoAtendimento lado) {
        return lado == LadoAtendimento.PACIENTE ? LadoAtendimento.PROFISSIONAL : LadoAtendimento.PACIENTE;
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new TransicaoStatusInvalidaException(mensagem);
        }
    }

}
