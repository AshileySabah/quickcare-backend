package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.common.RecursoNaoEncontradoException;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.ocorrencia.dto.ResumoOcorrenciasResponse;
import com.quickcare.backend.problema.ProblemaAtendimento;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OcorrenciaService {

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm").withZone(ZoneId.of("America/Sao_Paulo"));

    private final OcorrenciaRepository ocorrenciaRepository;
    private final BloqueioRepository bloqueioRepository;
    private final RecursoOcorrenciaRepository recursoRepository;
    private final UsuarioRepository usuarioRepository;
    private final Notificador notificador;
    private final Clock clock;

    @Transactional
    public void registrar(Usuario usuario, TipoPerfil perfil, MotivoOcorrencia motivo, Atendimento atendimento) {
        registrar(usuario, perfil, motivo, atendimento, null);
    }

    @Transactional
    public void registrar(Usuario usuario, TipoPerfil perfil, MotivoOcorrencia motivo, Atendimento atendimento,
                          ProblemaAtendimento problema) {
        if (motivo == MotivoOcorrencia.ALTERACAO_TARDIA_SOLICITACAO
                && ocorrenciaRepository.existsByUsuarioIdAndSolicitacaoIdAndMotivo(
                        usuario.getId(), atendimento.getSolicitacao().getId(), motivo)) {
            return;
        }

        salvarEAvaliar(Ocorrencia.builder()
                .usuario(usuario)
                .perfil(perfil)
                .motivo(motivo)
                .solicitacao(atendimento.getSolicitacao())
                .atendimento(atendimento)
                .problema(problema)
                .criadoEm(agora()));
    }

    @Transactional
    public Ocorrencia lancarManual(Usuario admin, Usuario usuario, TipoPerfil perfil, String justificativa) {
        return salvarEAvaliar(Ocorrencia.builder()
                .usuario(usuario)
                .perfil(perfil)
                .motivo(MotivoOcorrencia.MANUAL)
                .justificativa(justificativa.trim())
                .criadoPor(admin)
                .criadoEm(agora()));
    }

    @Transactional
    public Ocorrencia anular(Usuario admin, Long ocorrenciaId, String justificativa) {
        Ocorrencia ocorrencia = ocorrenciaRepository.findById(ocorrenciaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ocorrência não encontrada."));
        if (ocorrencia.anulada()) {
            throw new TransicaoStatusInvalidaException("Esta ocorrência já foi anulada.");
        }
        Instant agora = agora();
        ocorrencia.anular(admin, justificativa, agora);
        if (ocorrencia.getRecurso() != null && ocorrencia.getRecurso().emAnalise()) {
            ocorrencia.getRecurso().decidir(admin, true, justificativa, agora);
        }
        notificador.ocorrenciaAnulada(ocorrencia);
        desfazerBloqueioSemMotivo(ocorrencia.getUsuario().getId(), ocorrencia.getPerfil(), agora);
        return ocorrencia;
    }

    @Transactional
    public ResumoOcorrenciasResponse recorrer(Long usuarioId, TipoPerfil perfil, Long ocorrenciaId, String texto) {
        Ocorrencia ocorrencia = ocorrenciaRepository.findById(ocorrenciaId)
                .filter(o -> o.getUsuario().getId().equals(usuarioId) && o.getPerfil() == perfil)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Ocorrência não encontrada."));
        if (!ocorrencia.podeRecorrer()) {
            throw new TransicaoStatusInvalidaException(ocorrencia.getRecurso() != null
                    ? "Você já recorreu desta ocorrência."
                    : "Não cabe recurso desta ocorrência.");
        }
        RecursoOcorrencia recurso = recursoRepository.saveAndFlush(RecursoOcorrencia.builder()
                .ocorrencia(ocorrencia)
                .texto(texto.trim())
                .status(RecursoOcorrencia.Status.EM_ANALISE)
                .criadoEm(agora())
                .build());
        ocorrencia.setRecurso(recurso);
        return resumo(usuarioId, perfil);
    }

    @Transactional
    public RecursoOcorrencia decidirRecurso(Usuario admin, Long recursoId, boolean anular, String justificativa) {
        RecursoOcorrencia recurso = recursoRepository.findById(recursoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Recurso não encontrado."));
        if (!recurso.emAnalise()) {
            throw new TransicaoStatusInvalidaException("Este recurso já foi decidido.");
        }
        Instant agora = agora();
        recurso.decidir(admin, anular, justificativa, agora);
        Ocorrencia ocorrencia = recurso.getOcorrencia();
        if (anular && !ocorrencia.anulada()) {
            ocorrencia.anular(admin, justificativa, agora);
        }
        notificador.recursoDecidido(recurso);
        if (anular) {
            desfazerBloqueioSemMotivo(ocorrencia.getUsuario().getId(), ocorrencia.getPerfil(), agora);
        }
        return recurso;
    }

    @Transactional(readOnly = true)
    public long recursosEmAnalise() {
        return recursoRepository.countByStatus(RecursoOcorrencia.Status.EM_ANALISE);
    }

    @Transactional
    public void anularDoProblema(Usuario admin, ProblemaAtendimento problema, String justificativa) {
        Instant agora = Instant.now(clock);
        ocorrenciaRepository.findByProblemaIdAndAnuladaEmIsNull(problema.getId())
                .forEach(ocorrencia -> ocorrencia.anular(admin, justificativa, agora));
    }

    @Transactional
    public Bloqueio aplicarBloqueio(Usuario admin, Usuario usuario, TipoPerfil perfil, String justificativa) {
        Instant agora = agora();
        if (bloqueioAtivo(usuario.getId(), perfil).isPresent()) {
            throw new TransicaoStatusInvalidaException("Este perfil já está bloqueado.");
        }
        Bloqueio ultimo = bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(usuario.getId(), perfil)
                .orElse(null);
        int nivel = RegrasBloqueio.proximoNivel(ocorrenciaRepository.findMomentosValidos(usuario.getId(), perfil), ultimo);
        Bloqueio bloqueio = bloqueioRepository.save(Bloqueio.builder()
                .usuario(usuario)
                .perfil(perfil)
                .nivel(nivel)
                .inicio(agora)
                .fim(agora.plus(RegrasBloqueio.duracao(nivel)))
                .criadoPor(admin)
                .justificativa(justificativa.trim())
                .build());
        notificador.bloqueioAplicado(bloqueio);
        return bloqueio;
    }

    @Transactional
    public Bloqueio removerBloqueio(Usuario admin, Long bloqueioId, String justificativa) {
        Instant agora = Instant.now(clock);
        Bloqueio bloqueio = bloqueioRepository.findById(bloqueioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Bloqueio não encontrado."));
        if (!bloqueio.ativoEm(agora)) {
            throw new TransicaoStatusInvalidaException("Este bloqueio não está mais valendo.");
        }
        bloqueio.remover(admin, justificativa, agora);
        notificador.bloqueioRemovido(bloqueio);
        return bloqueio;
    }

    @Transactional(readOnly = true)
    public Optional<Bloqueio> bloqueioAtivo(Long usuarioId, TipoPerfil perfil) {
        Instant agora = Instant.now(clock);
        return bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(usuarioId, perfil)
                .filter(bloqueio -> bloqueio.ativoEm(agora));
    }

    @Transactional
    public Optional<Bloqueio> bloqueioVigente(Long usuarioId, TipoPerfil perfil) {
        Instant agora = agora();
        desfazerBloqueioSemMotivo(usuarioId, perfil, agora);
        return bloqueioAtivo(usuarioId, perfil)
                .or(() -> usuarioRepository.findById(usuarioId)
                        .flatMap(usuario -> avaliarBloqueio(usuario, perfil, agora)));
    }

    private void desfazerBloqueioSemMotivo(Long usuarioId, TipoPerfil perfil, Instant agora) {
        bloqueioAtivo(usuarioId, perfil).filter(bloqueio -> !bloqueio.aplicadoPeloAdmin()).ifPresent(bloqueio -> {
            Bloqueio anterior = bloqueioRepository
                    .findFirstByUsuarioIdAndPerfilAndDesfeitoFalseAndInicioBeforeOrderByInicioDesc(
                            usuarioId, perfil, bloqueio.getInicio())
                    .orElse(null);
            if (!RegrasBloqueio.aindaJustificado(ocorrenciaRepository.findMomentosValidos(usuarioId, perfil), anterior, bloqueio)) {
                bloqueio.desfazer(agora);
                notificador.bloqueioDesfeito(bloqueio);
            }
        });
    }

    @Transactional
    public void exigirSemBloqueio(Long usuarioId, TipoPerfil perfil) {
        bloqueioVigente(usuarioId, perfil).ifPresent(bloqueio -> {
            String acao = perfil == TipoPerfil.PACIENTE ? "criar solicitações" : "enviar propostas";
            throw new PerfilNaoAutorizadoException("Por excesso de ocorrências, você não pode " + acao
                    + " até " + DATA_HORA.format(bloqueio.getFim()) + ".");
        });
    }

    @Transactional(readOnly = true)
    public int contando(Long usuarioId, TipoPerfil perfil) {
        Instant agora = Instant.now(clock);
        return RegrasBloqueio.contando(ocorrenciaRepository.findMomentosValidos(usuarioId, perfil),
                corte(usuarioId, perfil, agora), agora).size();
    }

    @Transactional(readOnly = true)
    public Bloqueio corte(Long usuarioId, TipoPerfil perfil, Instant agora) {
        Bloqueio ultimo = bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(usuarioId, perfil)
                .orElse(null);
        Bloqueio anterior = ultimo == null || !ultimo.ativoEm(agora) ? null
                : bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseAndInicioBeforeOrderByInicioDesc(
                        usuarioId, perfil, ultimo.getInicio()).orElse(null);
        return RegrasBloqueio.corte(ultimo, anterior, agora);
    }

    @Transactional
    public ResumoOcorrenciasResponse resumo(Long usuarioId, TipoPerfil perfil) {
        Bloqueio bloqueio = bloqueioVigente(usuarioId, perfil).orElse(null);
        List<Ocorrencia> ocorrencias = ocorrenciaRepository.findByUsuarioIdAndPerfilOrderByCriadoEmDesc(usuarioId, perfil);
        return ResumoOcorrenciasResponse.de(contando(usuarioId, perfil), bloqueio,
                corte(usuarioId, perfil, Instant.now(clock)), ocorrencias);
    }

    private Ocorrencia salvarEAvaliar(Ocorrencia.OcorrenciaBuilder builder) {
        Ocorrencia ocorrencia = ocorrenciaRepository.save(builder.build());
        Usuario usuario = ocorrencia.getUsuario();
        TipoPerfil perfil = ocorrencia.getPerfil();
        Instant agora = ocorrencia.getCriadoEm();

        notificador.ocorrenciaRegistrada(ocorrencia, contando(usuario.getId(), perfil));
        avaliarBloqueio(usuario, perfil, agora);
        return ocorrencia;
    }

    private Optional<Bloqueio> avaliarBloqueio(Usuario usuario, TipoPerfil perfil, Instant agora) {
        Bloqueio ultimo = bloqueioRepository.findFirstByUsuarioIdAndPerfilAndDesfeitoFalseOrderByInicioDesc(usuario.getId(), perfil).orElse(null);
        List<Instant> validas = ocorrenciaRepository.findMomentosValidos(usuario.getId(), perfil);

        return RegrasBloqueio.nivelDoNovoBloqueio(validas, ultimo, agora).map(nivel -> {
            Bloqueio bloqueio = bloqueioRepository.save(Bloqueio.builder()
                    .usuario(usuario)
                    .perfil(perfil)
                    .nivel(nivel)
                    .inicio(agora)
                    .fim(agora.plus(RegrasBloqueio.duracao(nivel)))
                    .build());
            notificador.bloqueioAplicado(bloqueio);
            return bloqueio;
        });
    }

    private Instant agora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

}
