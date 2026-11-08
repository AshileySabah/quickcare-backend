package com.quickcare.backend.banimento;

import com.quickcare.backend.administrador.AcaoAdmin;
import com.quickcare.backend.administrador.AcessoAdmin;
import com.quickcare.backend.administrador.TipoAcaoAdmin;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.banimento.dto.BanimentoResponse;
import com.quickcare.backend.banimento.dto.SituacaoBanimentoResponse;
import com.quickcare.backend.common.RecursoNaoEncontradoException;
import com.quickcare.backend.denuncia.Denuncia;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoRepository;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class BanimentoService {

    public static final int DIAS_MAXIMOS = 365;
    private static final Set<StatusAtendimento> JA_ACONTECEU = EnumSet.of(
            StatusAtendimento.FINALIZADO, StatusAtendimento.CONCLUIDO, StatusAtendimento.PROBLEMA_RELATADO,
            StatusAtendimento.EM_DISPUTA, StatusAtendimento.NAO_REALIZADO);

    private final BanimentoRepository banimentoRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final SolicitacaoRepository solicitacaoRepository;
    private final PropostaRepository propostaRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final AtendimentoService atendimentoService;
    private final AcessoAdmin acessoAdmin;
    private final Notificador notificador;
    private final Clock clock;
    private final TransactionTemplate transacao;

    public BanimentoService(
            BanimentoRepository banimentoRepository,
            PacienteRepository pacienteRepository,
            ProfissionalRepository profissionalRepository,
            SolicitacaoRepository solicitacaoRepository,
            PropostaRepository propostaRepository,
            AtendimentoRepository atendimentoRepository,
            AtendimentoService atendimentoService,
            AcessoAdmin acessoAdmin,
            Notificador notificador,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.banimentoRepository = banimentoRepository;
        this.pacienteRepository = pacienteRepository;
        this.profissionalRepository = profissionalRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.propostaRepository = propostaRepository;
        this.atendimentoRepository = atendimentoRepository;
        this.atendimentoService = atendimentoService;
        this.acessoAdmin = acessoAdmin;
        this.notificador = notificador;
        this.clock = clock;
        this.transacao = new TransactionTemplate(transactionManager);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public Banimento banir(Usuario admin, Usuario usuario, TipoPerfil perfil, TipoBanimento tipo, Integer dias,
                           String justificativa, Denuncia denuncia) {
        Instant agora = Instant.now(clock);
        if (tipo == TipoBanimento.SUSPENSAO && (dias == null || dias < 1 || dias > DIAS_MAXIMOS)) {
            throw new TransicaoStatusInvalidaException("Informe de 1 a " + DIAS_MAXIMOS + " dias de suspensão.");
        }
        if (banimentoRepository.findAtivo(usuario.getId(), perfil, agora).isPresent()) {
            throw new TransicaoStatusInvalidaException(perfil == TipoPerfil.PACIENTE
                    ? "O perfil de paciente já está banido."
                    : "O perfil de profissional já está banido.");
        }

        Banimento banimento = banimentoRepository.save(Banimento.builder()
                .usuario(usuario)
                .perfil(perfil)
                .tipo(tipo)
                .inicio(agora)
                .fim(tipo == TipoBanimento.SUSPENSAO ? agora.plus(Duration.ofDays(dias)) : null)
                .justificativa(justificativa.trim())
                .aplicadoPor(admin)
                .denuncia(denuncia)
                .criadoEm(agora)
                .build());

        if (perfil == TipoPerfil.PACIENTE) {
            Paciente paciente = pacienteDo(usuario.getId());
            paciente.setBanido(true);
            encerrarDoPaciente(paciente);
        } else {
            Profissional profissional = profissionalDo(usuario.getId());
            profissional.setBanido(true);
            encerrarDoProfissional(profissional);
        }

        acessoAdmin.registrar(admin, TipoAcaoAdmin.BANIMENTO_APLICADO, AcaoAdmin.Alvo.BANIMENTO, banimento.getId(),
                perfil.name() + "; " + (tipo == TipoBanimento.PERMANENTE ? "permanente" : dias + " dias"), justificativa);
        notificador.banimentoAplicado(banimento);
        return banimento;
    }

    @Transactional
    public Banimento remover(Usuario admin, Long banimentoId, String justificativa) {
        Banimento banimento = buscar(banimentoId);
        Instant agora = Instant.now(clock);
        if (!banimento.ativo(agora)) {
            throw new TransicaoStatusInvalidaException("Este banimento já foi encerrado.");
        }
        banimento.encerrar(Banimento.Encerramento.REMOVIDO, admin, justificativa, agora);
        if (banimento.getRecursoStatus() == Banimento.StatusRecurso.EM_ANALISE) {
            banimento.decidirRecurso(true, admin, justificativa, agora);
        }
        liberar(banimento);
        acessoAdmin.registrar(admin, TipoAcaoAdmin.BANIMENTO_REMOVIDO, AcaoAdmin.Alvo.BANIMENTO, banimento.getId(),
                banimento.getPerfil().name(), justificativa);
        notificador.banimentoEncerrado(banimento);
        return banimento;
    }

    @Transactional
    public Banimento decidirRecurso(Usuario admin, Long banimentoId, boolean aceitar, String justificativa) {
        Banimento banimento = buscar(banimentoId);
        Instant agora = Instant.now(clock);
        banimento.decidirRecurso(aceitar, admin, justificativa, agora);
        if (aceitar && banimento.ativo(agora)) {
            banimento.encerrar(Banimento.Encerramento.RECURSO_ACEITO, admin, justificativa, agora);
            liberar(banimento);
        }
        acessoAdmin.registrar(admin, aceitar ? TipoAcaoAdmin.RECURSO_BANIMENTO_ACEITO : TipoAcaoAdmin.RECURSO_BANIMENTO_NEGADO,
                AcaoAdmin.Alvo.BANIMENTO, banimento.getId(), banimento.getPerfil().name(), justificativa);
        notificador.recursoDeBanimentoDecidido(banimento);
        return banimento;
    }

    @Transactional
    public SituacaoBanimentoResponse recorrer(Long usuarioId, TipoPerfil perfil, Long banimentoId, String texto) {
        Instant agora = Instant.now(clock);
        Banimento banimento = banimentoRepository.findById(banimentoId)
                .filter(encontrado -> encontrado.getUsuario().getId().equals(usuarioId) && encontrado.getPerfil() == perfil)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Banimento não encontrado."));
        banimento.recorrer(texto, agora);
        return new SituacaoBanimentoResponse(BanimentoResponse.paraOUsuario(banimento, agora));
    }

    @Transactional(readOnly = true)
    public SituacaoBanimentoResponse situacao(Long usuarioId, TipoPerfil perfil) {
        Instant agora = Instant.now(clock);
        return new SituacaoBanimentoResponse(banimentoRepository.findAtivo(usuarioId, perfil, agora)
                .map(banimento -> BanimentoResponse.paraOUsuario(banimento, agora))
                .orElse(null));
    }

    @Transactional(readOnly = true)
    public List<Banimento> historico(Long usuarioId, TipoPerfil perfil) {
        return banimentoRepository.findByUsuarioIdAndPerfilOrderByInicioDescIdDesc(usuarioId, perfil);
    }

    @Transactional(readOnly = true)
    public Optional<BanimentoResponse> daDenuncia(Long denunciaId) {
        Instant agora = Instant.now(clock);
        return banimentoRepository.findFirstByDenunciaIdOrderByIdDesc(denunciaId)
                .map(banimento -> BanimentoResponse.paraOAdmin(banimento, agora));
    }

    @Transactional(readOnly = true)
    public long recursosEmAnalise() {
        return banimentoRepository.countByRecursoStatus(Banimento.StatusRecurso.EM_ANALISE);
    }

    @Transactional(readOnly = true)
    public int recursosEmAnalise(Long usuarioId, TipoPerfil perfil) {
        return (int) banimentoRepository.countByRecursoStatusAndUsuarioIdAndPerfil(
                Banimento.StatusRecurso.EM_ANALISE, usuarioId, perfil);
    }

    public void processarVencidos() {
        Instant agora = Instant.now(clock);
        for (Long id : banimentoRepository.findIdsVencidos(agora)) {
            try {
                transacao.executeWithoutResult(status -> expirar(id, agora));
            } catch (RuntimeException ex) {
                log.warn("Não foi possível encerrar a suspensão {}: {}", id, ex.getMessage());
            }
        }
    }

    private void expirar(Long id, Instant agora) {
        Banimento banimento = banimentoRepository.findById(id).orElse(null);
        if (banimento == null || banimento.getEncerradoEm() != null || banimento.getFim() == null
                || agora.isBefore(banimento.getFim())) {
            return;
        }
        banimento.encerrar(Banimento.Encerramento.EXPIROU, null, null, banimento.getFim());
        if (banimento.getRecursoStatus() == Banimento.StatusRecurso.EM_ANALISE) {
            banimento.decidirRecurso(false, null, "A suspensão terminou antes da análise do recurso.", agora);
        }
        liberar(banimento);
        notificador.banimentoEncerrado(banimento);
    }

    private void liberar(Banimento banimento) {
        Long usuarioId = banimento.getUsuario().getId();
        if (banimento.getPerfil() == TipoPerfil.PACIENTE) {
            pacienteRepository.findByUsuarioId(usuarioId).ifPresent(paciente -> paciente.setBanido(false));
        } else {
            profissionalRepository.findByUsuarioId(usuarioId).ifPresent(profissional -> profissional.setBanido(false));
        }
    }

    private void encerrarDoPaciente(Paciente paciente) {
        for (Solicitacao solicitacao : solicitacaoRepository.findByPacienteIdOrderByCriadoEmDesc(paciente.getId())) {
            if (!StatusSolicitacao.EM_CURSO.contains(solicitacao.getStatus())) {
                continue;
            }
            StatusSolicitacao novoStatus = atendimentoRepository.existsBySolicitacaoIdAndStatusIn(solicitacao.getId(), JA_ACONTECEU)
                    ? StatusSolicitacao.CONCLUIDA
                    : StatusSolicitacao.CANCELADA;
            List<Proposta> afetadas = atendimentoService.encerrarPeloAdmin(solicitacao, novoStatus);
            notificador.encerradoPorBanimentoDoPaciente(afetadas);
        }
    }

    private void encerrarDoProfissional(Profissional profissional) {
        for (Proposta proposta : propostaRepository.findByProfissionalIdOrderByCriadoEmDesc(profissional.getId())) {
            if (proposta.getStatus() == StatusProposta.PENDENTE) {
                proposta.setStatus(StatusProposta.CANCELADA);
                propostaRepository.saveAndFlush(proposta);
                atendimentoService.recalcularDepoisDoAdmin(proposta.getSolicitacao());
                notificador.encerradoPorBanimentoDoProfissional(proposta, false);
            } else if (proposta.getStatus() == StatusProposta.ACEITA && atendimentoService.ativoDa(proposta).isPresent()) {
                atendimentoService.cancelarPeloAdmin(proposta);
                notificador.encerradoPorBanimentoDoProfissional(proposta, true);
            }
        }
    }

    private Banimento buscar(Long id) {
        return banimentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Banimento não encontrado."));
    }

    private Paciente pacienteDo(Long usuarioId) {
        return pacienteRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de paciente não encontrado."));
    }

    private Profissional profissionalDo(Long usuarioId) {
        return profissionalRepository.findByUsuarioId(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de profissional não encontrado."));
    }

}
