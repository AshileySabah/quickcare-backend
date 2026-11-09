package com.quickcare.backend.seguranca;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.common.RecursoNaoEncontradoException;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.paciente.PacienteRepository;
import com.quickcare.backend.problema.ProblemaRepository;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.profissional.ProfissionalRepository;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.PropostaService;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoRepository;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.usuario.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class EncerramentoService {

    public static final Duration PRAZO_RECUPERACAO = Duration.ofDays(30);
    public static final String NOME_PERFIL_ENCERRADO = "Perfil encerrado";
    static final String NOME_USUARIO_EXCLUIDO = "Usuário encerrado";
    private static final String DOMINIO_ENCERRADO = "@encerrado.quickcare.invalid";
    private static final Set<StatusAtendimento> JA_ACONTECEU = EnumSet.of(
            StatusAtendimento.FINALIZADO, StatusAtendimento.CONCLUIDO, StatusAtendimento.PROBLEMA_RELATADO,
            StatusAtendimento.EM_DISPUTA, StatusAtendimento.NAO_REALIZADO);

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfissionalRepository profissionalRepository;
    private final SolicitacaoRepository solicitacaoRepository;
    private final PropostaRepository propostaRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final AtendimentoService atendimentoService;
    private final PropostaService propostaService;
    private final ProblemaRepository problemaRepository;
    private final PendenciasDoPerfil pendencias;
    private final CodigoSegurancaService codigoSegurancaService;
    private final Notificador notificador;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final TransactionTemplate transacao;

    public EncerramentoService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ProfissionalRepository profissionalRepository,
            SolicitacaoRepository solicitacaoRepository,
            PropostaRepository propostaRepository,
            AtendimentoRepository atendimentoRepository,
            AtendimentoService atendimentoService,
            PropostaService propostaService,
            ProblemaRepository problemaRepository,
            PendenciasDoPerfil pendencias,
            CodigoSegurancaService codigoSegurancaService,
            Notificador notificador,
            PasswordEncoder passwordEncoder,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.usuarioRepository = usuarioRepository;
        this.pacienteRepository = pacienteRepository;
        this.profissionalRepository = profissionalRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.propostaRepository = propostaRepository;
        this.atendimentoRepository = atendimentoRepository;
        this.atendimentoService = atendimentoService;
        this.propostaService = propostaService;
        this.problemaRepository = problemaRepository;
        this.pendencias = pendencias;
        this.codigoSegurancaService = codigoSegurancaService;
        this.notificador = notificador;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.transacao = new TransactionTemplate(transactionManager);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public static Instant excluiEm(Instant encerradoEm) {
        return encerradoEm.plus(PRAZO_RECUPERACAO);
    }

    @Transactional
    public void encerrarPerfil(Usuario usuario, TipoPerfil perfil) {
        Instant agora = Instant.now(clock);
        if (perfil == TipoPerfil.PACIENTE) {
            Paciente paciente = pacienteRepository.findByUsuarioId(usuario.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Você não tem perfil de paciente ativo."));
            encerrarAtividadesDoPaciente(paciente);
            paciente.setUsuario(criarLapide(usuario, agora));
            paciente.setEncerradoEm(agora);
            pacienteRepository.saveAndFlush(paciente);
        } else {
            Profissional profissional = profissionalRepository.findByUsuarioId(usuario.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Você não tem perfil de profissional ativo."));
            encerrarAtividadesDoProfissional(profissional);
            profissional.setUsuario(criarLapide(usuario, agora));
            profissional.setEncerradoEm(agora);
            profissionalRepository.saveAndFlush(profissional);
        }
        if (usuario.getUltimoPerfil() == perfil) {
            usuario.setUltimoPerfil(outro(perfil));
        }
    }

    @Transactional
    public void encerrarConta(Usuario usuario) {
        Instant agora = Instant.now(clock);
        if (pacienteRepository.findByUsuarioId(usuario.getId()).isPresent()) {
            encerrarPerfil(usuario, TipoPerfil.PACIENTE);
        }
        if (profissionalRepository.findByUsuarioId(usuario.getId()).isPresent()) {
            encerrarPerfil(usuario, TipoPerfil.PROFISSIONAL);
        }
        usuario.setEncerradoEm(agora);
        usuario.setSenhaAlteradaEm(agora);
    }

    @Transactional
    public List<TipoPerfil> recuperarConta(Usuario usuario) {
        if (!usuario.contaEncerrada()) {
            throw new TransicaoStatusInvalidaException("Esta conta não está encerrada.");
        }
        Instant agora = Instant.now(clock);
        List<TipoPerfil> recuperados = new ArrayList<>();
        pacienteRepository.findCongeladoDoTitular(usuario.getId()).ifPresent(paciente -> {
            restaurar(usuario, paciente, agora);
            recuperados.add(TipoPerfil.PACIENTE);
        });
        profissionalRepository.findCongeladoDoTitular(usuario.getId()).ifPresent(profissional -> {
            restaurar(usuario, profissional, agora);
            recuperados.add(TipoPerfil.PROFISSIONAL);
        });
        usuario.setEncerradoEm(null);
        if (!recuperados.isEmpty() && (usuario.getUltimoPerfil() == null || !recuperados.contains(usuario.getUltimoPerfil()))) {
            usuario.setUltimoPerfil(recuperados.getFirst());
        }
        return recuperados;
    }

    @Transactional
    public void recuperarPerfil(Usuario usuario, TipoPerfil perfil) {
        Instant agora = Instant.now(clock);
        if (perfil == TipoPerfil.PACIENTE) {
            Paciente paciente = pacienteRepository.findCongeladoDoTitular(usuario.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Não há perfil de paciente encerrado para recuperar."));
            restaurar(usuario, paciente, agora);
        } else {
            Profissional profissional = profissionalRepository.findCongeladoDoTitular(usuario.getId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Não há perfil de profissional encerrado para recuperar."));
            restaurar(usuario, profissional, agora);
        }
        usuario.setUltimoPerfil(perfil);
    }

    public void processarVencidos() {
        Instant agora = Instant.now(clock);
        Instant limite = agora.minus(PRAZO_RECUPERACAO);
        for (Long id : pacienteRepository.findIdsParaExcluir(limite)) {
            executar("perfil de paciente " + id, () -> excluirPaciente(id, agora));
        }
        for (Long id : profissionalRepository.findIdsParaExcluir(limite)) {
            executar("perfil de profissional " + id, () -> excluirProfissional(id, agora));
        }
        for (Long id : usuarioRepository.findIdsParaExcluir(limite)) {
            executar("conta " + id, () -> excluirUsuario(id, agora));
        }
    }

    private void executar(String alvo, Runnable tarefa) {
        try {
            transacao.executeWithoutResult(status -> tarefa.run());
        } catch (RuntimeException ex) {
            log.warn("Não foi possível excluir {}: {}", alvo, ex.getMessage());
        }
    }

    private void excluirPaciente(Long id, Instant agora) {
        Paciente paciente = pacienteRepository.findById(id).orElse(null);
        if (paciente == null || !paciente.congelado()) {
            return;
        }
        paciente.anonimizar(agora);
        paciente.getUsuario().setTitular(null);
        paciente.getUsuario().setExcluidoEm(agora);
    }

    private void excluirProfissional(Long id, Instant agora) {
        Profissional profissional = profissionalRepository.findById(id).orElse(null);
        if (profissional == null || !profissional.congelado()) {
            return;
        }
        profissional.anonimizar(agora);
        profissional.getUsuario().setTitular(null);
        profissional.getUsuario().setExcluidoEm(agora);
    }

    private void excluirUsuario(Long id, Instant agora) {
        Usuario usuario = usuarioRepository.findById(id).orElse(null);
        if (usuario == null || !usuario.contaEncerrada()) {
            return;
        }
        codigoSegurancaService.descartarTodos(usuario);
        usuario.setCpfEncerrado(usuario.getCpf());
        usuario.setCpf(null);
        usuario.setEmail(UUID.randomUUID() + DOMINIO_ENCERRADO);
        usuario.setNome(NOME_USUARIO_EXCLUIDO);
        usuario.setTelefone(null);
        usuario.setAvatarStorageKey(null);
        usuario.setDocumentoIdentidadeTipo(null);
        usuario.setDocumentoIdentidadeNumero(null);
        usuario.setDocumentoIdentidadeUf(null);
        usuario.setSenhaHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        usuario.setExcluidoEm(agora);
    }

    private void restaurar(Usuario titular, Paciente paciente, Instant agora) {
        Usuario lapide = paciente.getUsuario();
        Instant congeladoEm = paciente.getEncerradoEm();
        Duration congelado = Duration.between(congeladoEm, agora);
        paciente.setUsuario(titular);
        paciente.setEncerradoEm(null);
        descartarLapide(lapide, agora);
        pendencias.prorrogar(titular, TipoPerfil.PACIENTE, congeladoEm, congelado);
        problemaRepository.findAguardandoDoPaciente(paciente.getId())
                .forEach(problema -> problema.prorrogarPorCongelamento(congelado));
        paciente.setBanido(pendencias.banido(titular, TipoPerfil.PACIENTE, agora));
        pacienteRepository.saveAndFlush(paciente);
        notificador.perfilRecuperado(titular, TipoPerfil.PACIENTE, pendencias.descrever(titular, TipoPerfil.PACIENTE, agora));
    }

    private void restaurar(Usuario titular, Profissional profissional, Instant agora) {
        Usuario lapide = profissional.getUsuario();
        Instant congeladoEm = profissional.getEncerradoEm();
        Duration congelado = Duration.between(congeladoEm, agora);
        profissional.setUsuario(titular);
        profissional.setEncerradoEm(null);
        descartarLapide(lapide, agora);
        pendencias.prorrogar(titular, TipoPerfil.PROFISSIONAL, congeladoEm, congelado);
        problemaRepository.findAguardandoDoProfissional(profissional.getId())
                .forEach(problema -> problema.prorrogarPorCongelamento(congelado));
        profissional.setBanido(pendencias.banido(titular, TipoPerfil.PROFISSIONAL, agora));
        profissionalRepository.saveAndFlush(profissional);
        notificador.perfilRecuperado(titular, TipoPerfil.PROFISSIONAL,
                pendencias.descrever(titular, TipoPerfil.PROFISSIONAL, agora));
    }

    private static void descartarLapide(Usuario lapide, Instant agora) {
        lapide.setTitular(null);
        lapide.setCpfEncerrado(null);
        lapide.setExcluidoEm(agora);
    }

    private Usuario criarLapide(Usuario titular, Instant agora) {
        return usuarioRepository.save(Usuario.builder()
                .nome(NOME_PERFIL_ENCERRADO)
                .email(UUID.randomUUID() + DOMINIO_ENCERRADO)
                .senhaHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .cpfEncerrado(titular.getCpf())
                .lapide(true)
                .titular(titular)
                .encerradoEm(agora)
                .build());
    }

    private void encerrarAtividadesDoPaciente(Paciente paciente) {
        for (Solicitacao solicitacao : solicitacaoRepository.findByPacienteIdOrderByCriadoEmDesc(paciente.getId())) {
            if (!StatusSolicitacao.EM_CURSO.contains(solicitacao.getStatus())) {
                continue;
            }
            List<Proposta> afetadas = new ArrayList<>(atendimentoRepository
                    .findBySolicitacaoIdAndStatusIn(solicitacao.getId(), StatusAtendimento.ATIVOS).stream()
                    .map(Atendimento::getProposta)
                    .toList());
            List<Proposta> pendentes = propostaRepository.findBySolicitacaoIdAndStatus(solicitacao.getId(), StatusProposta.PENDENTE);
            pendentes.forEach(proposta -> proposta.setStatus(StatusProposta.RECUSADA));
            afetadas.addAll(pendentes);
            atendimentoService.solicitacaoCancelada(solicitacao);
            solicitacao.setStatus(atendimentoRepository.existsBySolicitacaoIdAndStatusIn(solicitacao.getId(), JA_ACONTECEU)
                    ? StatusSolicitacao.CONCLUIDA
                    : StatusSolicitacao.CANCELADA);
            solicitacaoRepository.saveAndFlush(solicitacao);
            notificador.solicitacaoCancelada(afetadas);
        }
    }

    private void encerrarAtividadesDoProfissional(Profissional profissional) {
        for (Proposta proposta : propostaRepository.findByProfissionalIdOrderByCriadoEmDesc(profissional.getId())) {
            boolean pendente = proposta.getStatus() == StatusProposta.PENDENTE;
            boolean aceitaEmCurso = proposta.getStatus() == StatusProposta.ACEITA && atendimentoService.ativoDa(proposta).isPresent();
            if (pendente || aceitaEmCurso) {
                propostaService.encerrarPeloProfissional(proposta);
            }
        }
    }

    private static TipoPerfil outro(TipoPerfil perfil) {
        return perfil == TipoPerfil.PACIENTE ? TipoPerfil.PROFISSIONAL : TipoPerfil.PACIENTE;
    }

}
