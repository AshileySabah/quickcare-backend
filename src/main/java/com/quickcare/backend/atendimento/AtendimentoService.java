package com.quickcare.backend.atendimento;

import com.quickcare.backend.atendimento.dto.AtendimentoResponse;
import com.quickcare.backend.atendimento.dto.RemarcacaoRequest;
import com.quickcare.backend.atendimento.exception.AtendimentoNaoEncontradoException;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.ocorrencia.MotivoOcorrencia;
import com.quickcare.backend.ocorrencia.OcorrenciaService;
import com.quickcare.backend.ocorrencia.RegrasOcorrencia;
import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.proposta.PropostaRepository;
import com.quickcare.backend.proposta.StatusProposta;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
import com.quickcare.backend.solicitacao.StatusSolicitacao;
import com.quickcare.backend.solicitacao.exception.PerfilNaoAutorizadoException;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Regras do atendimento: ocupar a vaga no aceite (inclusive tomando a reservada
 * de quem não reaceitou), reaceite depois que o paciente edita, remarcação,
 * cancelamento (com as ocorrências da regra das 48h) e as mudanças que
 * acontecem sozinhas no horário (início, término, conclusão e lembretes).
 */
@Slf4j
@Service
public class AtendimentoService {

    /** Depois do término, a janela para relatar problema antes de concluir. */
    public static final Duration JANELA_CONCLUSAO = Duration.ofHours(72);
    static final Duration ANTECEDENCIA_VESPERA = Duration.ofHours(24);
    static final Duration ANTECEDENCIA_LEMBRETE = Duration.ofMinutes(30);
    /** Até quanto depois do início ainda vale avisar "começou agora" (o servidor pode ter dormido). */
    static final Duration TOLERANCIA_LEMBRETE_INICIO = Duration.ofMinutes(30);
    static final Duration DURACAO_MAXIMA = Duration.ofDays(366);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Set<StatusAtendimento> ANTES_DO_INICIO =
            Set.of(StatusAtendimento.AGENDADO, StatusAtendimento.AGUARDANDO_REACEITE);

    private final AtendimentoRepository atendimentoRepository;
    private final AtendimentoEventoRepository eventoRepository;
    private final PropostaRepository propostaRepository;
    private final OcorrenciaService ocorrenciaService;
    private final Notificador notificador;
    private final Clock clock;
    private final TransactionTemplate transacao;

    public AtendimentoService(
            AtendimentoRepository atendimentoRepository,
            AtendimentoEventoRepository eventoRepository,
            PropostaRepository propostaRepository,
            OcorrenciaService ocorrenciaService,
            Notificador notificador,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.atendimentoRepository = atendimentoRepository;
        this.eventoRepository = eventoRepository;
        this.propostaRepository = propostaRepository;
        this.ocorrenciaService = ocorrenciaService;
        this.notificador = notificador;
        this.clock = clock;
        this.transacao = new TransactionTemplate(transactionManager);
        // Independente de quem chama (as leituras são read-only): cada atendimento confirma sozinho.
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ================================================= chamados pelas propostas

    /**
     * O paciente aceitou: a proposta ocupa uma vaga livre da linha dela ou,
     * sem livre, a reservada de um profissional que ainda não reaceitou (ele é
     * substituído). Pendentes que não cabem em mais nada são recusadas.
     */
    @Transactional
    public Atendimento aceitarProposta(Proposta proposta) {
        Instant agora = Instant.now(clock);
        Solicitacao solicitacao = proposta.getSolicitacao();
        exigir(proposta.getInicio().isAfter(agora),
                "O início desta proposta já passou. Peça ao profissional para atualizar a data.");

        SolicitacaoProfissional vaga = solicitacao.vagaDisponivelPara(proposta.getProfissional())
                .orElseGet(() -> tomarVagaReservada(proposta, agora));
        vaga.preencherVaga();
        proposta.setVaga(vaga);
        proposta.setStatus(StatusProposta.ACEITA);
        proposta.setMotivoReaceite(null);

        Atendimento atendimento = atendimentoRepository.save(Atendimento.agendar(proposta, agora));
        registrar(atendimento, TipoEventoAtendimento.AGENDADO, LadoAtendimento.PACIENTE, null);

        recusarPendentesSemVaga(solicitacao, proposta);
        recalcularSolicitacao(solicitacao);
        return atendimento;
    }

    /** Atendimento ativo da proposta, se houver. */
    @Transactional(readOnly = true)
    public Optional<Atendimento> ativoDa(Proposta proposta) {
        return atendimentoRepository.findFirstByPropostaIdAndStatusIn(proposta.getId(), StatusAtendimento.ATIVOS);
    }

    /**
     * O profissional alterou a proposta aceita: ela deixa de estar aceita e o
     * paciente precisa aceitá-la de novo. Só antes do início — depois, ele
     * remarca ou cancela.
     */
    @Transactional
    public void cancelarPorAlteracaoDaProposta(Proposta proposta) {
        ativoDa(proposta).ifPresent(atendimento -> {
            exigir(atendimento.getStatus() != StatusAtendimento.EM_ANDAMENTO,
                    "O atendimento já começou: remarque ou cancele em vez de editar a proposta.");
            encerrar(atendimento, LadoAtendimento.PROFISSIONAL, MotivoCancelamento.PROPOSTA_ALTERADA,
                    proposta.getStatus(), Instant.now(clock), false);
        });
    }

    /** O profissional cancelou a proposta aceita: é cancelar o atendimento (ou recusar o reaceite). */
    @Transactional
    public void cancelarPeloProfissional(Proposta proposta) {
        ativoDa(proposta).ifPresent(atendimento -> {
            if (atendimento.getStatus() == StatusAtendimento.AGUARDANDO_REACEITE) {
                recusarReaceite(atendimento);
            } else {
                cancelar(atendimento, LadoAtendimento.PROFISSIONAL);
            }
        });
    }

    // ============================================== chamados pelas solicitações

    /** A edição troca as linhas; antes, as propostas aceitas saem das antigas (que serão apagadas). */
    @Transactional
    public void prepararEdicao(Solicitacao solicitacao) {
        exigir(!atendimentoRepository.existsBySolicitacaoIdAndStatusIn(
                        solicitacao.getId(), Set.of(StatusAtendimento.EM_ANDAMENTO)),
                "Há um atendimento em andamento nesta solicitação. Remarque ou cancele o atendimento em vez de editar.");
        exigir(!atendimentoRepository.existsBySolicitacaoIdAndStatusIn(solicitacao.getId(), StatusAtendimento.EM_PROBLEMA),
                "Há um problema relatado em um atendimento desta solicitação. Espere ele ser resolvido para editar.");
        propostaRepository.findBySolicitacaoIdAndStatus(solicitacao.getId(), StatusProposta.ACEITA)
                .forEach(proposta -> proposta.setVaga(null));
    }

    /**
     * Depois da edição (linhas novas já salvas): os já realizados voltam às
     * vagas; os que ainda vão acontecer reservam uma e aguardam o reaceite do
     * profissional. Quem não cabe mais na nova versão é cancelado (sem
     * penalidade para ele). Devolve as propostas que aguardam reaceite.
     */
    @Transactional
    public List<Proposta> solicitacaoEditada(Solicitacao solicitacao) {
        Instant agora = Instant.now(clock);
        List<Atendimento> atendimentos = atendimentoRepository.findBySolicitacaoIdAndStatusIn(
                solicitacao.getId(), Set.of(StatusAtendimento.values()));

        atendimentos.stream()
                .filter(atendimento -> StatusAtendimento.REALIZADOS.contains(atendimento.getStatus()))
                .forEach(atendimento -> solicitacao.vagaDisponivelPara(atendimento.getProposta().getProfissional())
                        .ifPresent(linha -> {
                            linha.preencherVaga();
                            atendimento.getProposta().setVaga(linha);
                        }));

        List<Proposta> aguardandoReaceite = new ArrayList<>();
        atendimentos.stream()
                .filter(atendimento -> ANTES_DO_INICIO.contains(atendimento.getStatus()))
                .sorted(Comparator.comparing(Atendimento::getAceitoEm))
                .forEach(atendimento -> {
                    Proposta proposta = atendimento.getProposta();
                    Optional<SolicitacaoProfissional> linha = solicitacao.vagaDisponivelPara(proposta.getProfissional());
                    if (linha.isPresent()) {
                        linha.get().preencherVaga();
                        linha.get().reservarVaga();
                        proposta.setVaga(linha.get());
                        atendimento.solicitarReaceite(agora);
                        registrar(atendimento, TipoEventoAtendimento.REACEITE_SOLICITADO, LadoAtendimento.PACIENTE, null);
                        aguardandoReaceite.add(proposta);
                    } else {
                        // Já está fora da vaga (prepararEdicao); só encerra.
                        boolean tardia = RegrasOcorrencia.alteracaoTardia(atendimento, agora);
                        atendimento.cancelar(LadoAtendimento.SISTEMA, MotivoCancelamento.EDICAO_INCOMPATIVEL, agora);
                        proposta.setStatus(StatusProposta.RECUSADA);
                        registrar(atendimento, TipoEventoAtendimento.CANCELADO, LadoAtendimento.PACIENTE,
                                "A nova versão da solicitação não tem vaga para este profissional.");
                        if (tardia) {
                            ocorrenciaDoPaciente(atendimento, MotivoOcorrencia.ALTERACAO_TARDIA_SOLICITACAO);
                        }
                        notificador.edicaoIncompativel(proposta);
                    }
                });

        recalcularSolicitacao(solicitacao);
        return aguardandoReaceite;
    }

    /**
     * O paciente cancelou a solicitação: os atendimentos que ainda iam
     * acontecer caem. Conta no máximo uma ocorrência para ele, se algum desses
     * cancelamentos contaria pela regra das 48h.
     */
    @Transactional
    public void solicitacaoCancelada(Solicitacao solicitacao) {
        Instant agora = Instant.now(clock);
        boolean ocorrenciaRegistrada = false;
        for (Atendimento atendimento : atendimentoRepository.findBySolicitacaoIdAndStatusIn(
                solicitacao.getId(), StatusAtendimento.ATIVOS)) {
            boolean conta = RegrasOcorrencia.cancelamentoConta(atendimento, agora);
            encerrar(atendimento, LadoAtendimento.PACIENTE, MotivoCancelamento.SOLICITACAO_CANCELADA,
                    StatusProposta.ACEITA, agora, false);
            if (conta && !ocorrenciaRegistrada) {
                ocorrenciaDoPaciente(atendimento, MotivoOcorrencia.CANCELAMENTO_ATENDIMENTO);
                ocorrenciaRegistrada = true;
            }
        }
    }

    // ================================================= ações dos dois lados

    @Transactional
    public AtendimentoResponse reaceitar(Long usuarioId, Long id) {
        Atendimento atendimento = buscarDoLado(usuarioId, id, LadoAtendimento.PROFISSIONAL);
        Instant agora = Instant.now(clock);

        atendimento.reaceitar(agora);
        atendimento.getProposta().getVaga().confirmarReserva();
        registrar(atendimento, TipoEventoAtendimento.REACEITO, LadoAtendimento.PROFISSIONAL, null);

        Solicitacao solicitacao = atendimento.getSolicitacao();
        recusarPendentesSemVaga(solicitacao, null);
        recalcularSolicitacao(solicitacao);
        notificador.reaceiteRespondido(atendimento.getProposta(), true);
        return responder(atendimento);
    }

    @Transactional
    public AtendimentoResponse recusarReaceite(Long usuarioId, Long id) {
        Atendimento atendimento = buscarDoLado(usuarioId, id, LadoAtendimento.PROFISSIONAL);
        exigir(atendimento.getStatus() == StatusAtendimento.AGUARDANDO_REACEITE, "Este atendimento não aguarda reaceite.");
        recusarReaceite(atendimento);
        return responder(atendimento);
    }

    /** Recusar a nova versão não penaliza o profissional; o paciente responde se editou em cima da hora. */
    private void recusarReaceite(Atendimento atendimento) {
        boolean tardia = RegrasOcorrencia.alteracaoTardia(atendimento, atendimento.getReaceiteSolicitadoEm());
        encerrar(atendimento, LadoAtendimento.PROFISSIONAL, MotivoCancelamento.REACEITE_RECUSADO,
                StatusProposta.CANCELADA, Instant.now(clock), false);
        if (tardia) {
            ocorrenciaDoPaciente(atendimento, MotivoOcorrencia.ALTERACAO_TARDIA_SOLICITACAO);
        }
        notificador.reaceiteRespondido(atendimento.getProposta(), false);
    }

    @Transactional
    public AtendimentoResponse solicitarRemarcacao(Long usuarioId, Long id, RemarcacaoRequest request) {
        Atendimento atendimento = buscarDoUsuario(usuarioId, id);
        LadoAtendimento lado = ladoDe(atendimento, usuarioId);
        exigir(Duration.between(request.inicio(), request.termino()).compareTo(DURACAO_MAXIMA) <= 0,
                "O atendimento pode durar no máximo um ano.");

        atendimento.solicitarRemarcacao(lado, request.inicio(), request.termino(), Instant.now(clock));
        registrar(atendimento, TipoEventoAtendimento.REMARCACAO_SOLICITADA, lado,
                "Proposto: " + request.inicio() + " a " + request.termino());
        notificador.remarcacaoSolicitada(atendimento);
        return responder(atendimento);
    }

    @Transactional
    public AtendimentoResponse responderRemarcacao(Long usuarioId, Long id, boolean aceitar) {
        Atendimento atendimento = buscarDoUsuario(usuarioId, id);
        LadoAtendimento lado = ladoDe(atendimento, usuarioId);
        LadoAtendimento pedidaPor = atendimento.getRemarcacaoPor();

        if (aceitar) {
            atendimento.aceitarRemarcacao(lado, Instant.now(clock));
            registrar(atendimento, TipoEventoAtendimento.REMARCACAO_ACEITA, lado, null);
        } else {
            atendimento.recusarRemarcacao(lado);
            registrar(atendimento, TipoEventoAtendimento.REMARCACAO_RECUSADA, lado, null);
        }
        notificador.remarcacaoRespondida(atendimento, pedidaPor, aceitar);
        return responder(atendimento);
    }

    /** A qualquer momento até o término; a regra das 48h decide se conta como ocorrência. */
    @Transactional
    public AtendimentoResponse cancelar(Long usuarioId, Long id) {
        Atendimento atendimento = buscarDoUsuario(usuarioId, id);
        LadoAtendimento lado = ladoDe(atendimento, usuarioId);

        if (atendimento.getStatus() == StatusAtendimento.AGUARDANDO_REACEITE && lado == LadoAtendimento.PROFISSIONAL) {
            recusarReaceite(atendimento);
        } else {
            cancelar(atendimento, lado);
        }
        return responder(atendimento);
    }

    private void cancelar(Atendimento atendimento, LadoAtendimento lado) {
        Instant agora = Instant.now(clock);
        MotivoOcorrencia ocorrencia = ocorrenciaDoCancelamento(atendimento, agora).orElse(null);
        encerrarPorDesistencia(atendimento, lado, agora);
        if (ocorrencia != null) {
            ocorrenciaService.registrar(usuarioDo(atendimento, lado), perfilDo(lado), ocorrencia, atendimento);
        }
    }

    /**
     * Se quem cancela agora leva ocorrência: pela regra das 48h ou, aguardando
     * reaceite, se o paciente editou em cima da hora (prejudicou o profissional).
     */
    public static Optional<MotivoOcorrencia> ocorrenciaDoCancelamento(Atendimento atendimento, Instant agora) {
        if (atendimento.getStatus() == StatusAtendimento.AGUARDANDO_REACEITE) {
            return RegrasOcorrencia.alteracaoTardia(atendimento, atendimento.getReaceiteSolicitadoEm())
                    ? Optional.of(MotivoOcorrencia.ALTERACAO_TARDIA_SOLICITACAO) : Optional.empty();
        }
        return RegrasOcorrencia.cancelamentoConta(atendimento, agora)
                ? Optional.of(MotivoOcorrencia.CANCELAMENTO_ATENDIMENTO) : Optional.empty();
    }

    private void encerrarPorDesistencia(Atendimento atendimento, LadoAtendimento lado, Instant agora) {
        encerrar(atendimento, lado, MotivoCancelamento.DESISTENCIA,
                lado == LadoAtendimento.PROFISSIONAL ? StatusProposta.CANCELADA : StatusProposta.RECUSADA, agora, true);
        notificador.atendimentoCancelado(atendimento, lado);
    }

    // =========================================== bloqueio entre usuários (4b)

    /**
     * Um bloqueou o outro: o que estava vigente entre os dois acaba, como se
     * quem bloqueou tivesse desistido (para o outro, o aviso é o de sempre).
     * Atendimentos: cancelados (o profissional que aguardava reaceite só
     * recusa a nova versão); conta no máximo uma ocorrência para quem
     * bloqueou, pela regra das 48h. Propostas pendentes: recusadas (paciente
     * bloqueou) ou canceladas (profissional bloqueou). Os já realizados ficam.
     */
    @Transactional
    public void encerrarPorBloqueio(Long pacienteId, Long profissionalId, LadoAtendimento quemBloqueou) {
        Instant agora = Instant.now(clock);
        Atendimento comOcorrencia = null;
        MotivoOcorrencia motivo = null;

        for (Atendimento atendimento : atendimentoRepository.findByPropostaProfissionalIdAndSolicitacaoPacienteIdAndStatusIn(
                profissionalId, pacienteId, StatusAtendimento.ATIVOS)) {
            if (atendimento.getStatus() == StatusAtendimento.AGUARDANDO_REACEITE
                    && quemBloqueou == LadoAtendimento.PROFISSIONAL) {
                recusarReaceite(atendimento);
                continue;
            }
            Optional<MotivoOcorrencia> conta = ocorrenciaDoCancelamento(atendimento, agora);
            if (motivo == null && conta.isPresent()) {
                motivo = conta.get();
                comOcorrencia = atendimento;
            }
            encerrarPorDesistencia(atendimento, quemBloqueou, agora);
        }
        if (comOcorrencia != null) {
            ocorrenciaService.registrar(usuarioDo(comOcorrencia, quemBloqueou), perfilDo(quemBloqueou), motivo, comOcorrencia);
        }

        for (Proposta pendente : propostaRepository.findByProfissionalIdAndSolicitacaoPacienteIdAndStatus(
                profissionalId, pacienteId, StatusProposta.PENDENTE)) {
            pendente.setStatus(quemBloqueou == LadoAtendimento.PACIENTE ? StatusProposta.RECUSADA : StatusProposta.CANCELADA);
            propostaRepository.saveAndFlush(pendente);
            recalcularSolicitacao(pendente.getSolicitacao());
            notificador.propostaEncerradaPorBloqueio(pendente, quemBloqueou);
        }
    }

    // ===================================================== mudanças no horário

    /**
     * Aplica o que já venceu: início, término, conclusão, reaceite não feito e
     * lembretes. Roda pelo job a cada minuto e antes das leituras (o Render
     * free pode ter dormido). Cada atendimento numa transação própria, para uma
     * falha não travar os outros.
     */
    public void processarVencidos() {
        Instant agora = Instant.now(clock);
        for (Long id : atendimentoRepository.findIdsComTransicaoVencida(
                ANTES_DO_INICIO, agora, agora.minus(JANELA_CONCLUSAO))) {
            executar(id, () -> transicionar(id, agora));
        }
        for (Long id : atendimentoRepository.findIdsComLembreteDevido(
                agora.plus(ANTECEDENCIA_VESPERA), agora.plus(ANTECEDENCIA_LEMBRETE))) {
            executar(id, () -> lembrar(id, agora));
        }
    }

    private void executar(Long id, Runnable tarefa) {
        try {
            transacao.executeWithoutResult(status -> tarefa.run());
        } catch (RuntimeException ex) {
            // Outra execução (job ou leitura) pode ter mexido nele primeiro (versão); tenta na próxima.
            log.warn("Não foi possível processar o atendimento {}: {}", id, ex.getMessage());
        }
    }

    private void transicionar(Long id, Instant agora) {
        Atendimento atendimento = atendimentoRepository.findWithPartesById(id).orElse(null);
        if (atendimento == null) {
            return;
        }

        if (atendimento.getStatus() == StatusAtendimento.AGUARDANDO_REACEITE && !agora.isBefore(atendimento.getInicio())) {
            boolean tardia = RegrasOcorrencia.alteracaoTardia(atendimento, atendimento.getReaceiteSolicitadoEm());
            encerrar(atendimento, LadoAtendimento.SISTEMA, MotivoCancelamento.SEM_REACEITE,
                    StatusProposta.CANCELADA, agora, false);
            if (tardia) {
                ocorrenciaDoPaciente(atendimento, MotivoOcorrencia.ALTERACAO_TARDIA_SOLICITACAO);
            }
            notificador.semReaceite(atendimento);
            return;
        }

        if (atendimento.getStatus() == StatusAtendimento.AGENDADO && !agora.isBefore(atendimento.getInicio())) {
            boolean remarcacaoExpirou = atendimento.temRemarcacaoPendente();
            atendimento.iniciar();
            if (remarcacaoExpirou && !atendimento.temRemarcacaoPendente()) {
                registrar(atendimento, TipoEventoAtendimento.REMARCACAO_EXPIRADA, LadoAtendimento.SISTEMA, null);
            }
            registrar(atendimento, TipoEventoAtendimento.INICIADO, LadoAtendimento.SISTEMA, null);
            if (atendimento.getLembreteInicioEm() == null
                    && agora.isBefore(atendimento.getInicio().plus(TOLERANCIA_LEMBRETE_INICIO))) {
                notificador.lembrete(atendimento, Notificador.Lembrete.INICIO);
            }
            marcarLembretesEnviados(atendimento, agora);
        }

        if (atendimento.getStatus() == StatusAtendimento.EM_ANDAMENTO && !agora.isBefore(atendimento.getTermino())) {
            atendimento.finalizar();
            registrar(atendimento, TipoEventoAtendimento.FINALIZADO, LadoAtendimento.SISTEMA, null);
            notificador.atendimentoFinalizado(atendimento);
        }

        if (atendimento.getStatus() == StatusAtendimento.FINALIZADO
                && !agora.isBefore(atendimento.getTermino().plus(JANELA_CONCLUSAO))) {
            atendimento.concluir();
            registrar(atendimento, TipoEventoAtendimento.CONCLUIDO, LadoAtendimento.SISTEMA, null);
        }

        concluirSolicitacaoSeTerminou(atendimento.getSolicitacao());
    }

    /**
     * Na véspera (24h antes) e 30 minutos antes; o do horário sai junto com o
     * início. Quem aceitou já dentro da janela não recebe o lembrete dela.
     */
    private void lembrar(Long id, Instant agora) {
        Atendimento atendimento = atendimentoRepository.findWithPartesById(id).orElse(null);
        if (atendimento == null || atendimento.getStatus() != StatusAtendimento.AGENDADO) {
            return;
        }

        Instant marcoTrintaMinutos = atendimento.getInicio().minus(ANTECEDENCIA_LEMBRETE);
        Instant marcoVespera = atendimento.getInicio().minus(ANTECEDENCIA_VESPERA);

        if (atendimento.getLembrete30minEm() == null && !agora.isBefore(marcoTrintaMinutos)) {
            if (atendimento.getAceitoEm().isBefore(marcoTrintaMinutos)) {
                notificador.lembrete(atendimento, Notificador.Lembrete.TRINTA_MINUTOS);
            }
            atendimento.setLembrete30minEm(agora);
            if (atendimento.getLembrete24hEm() == null) {
                atendimento.setLembrete24hEm(agora);
            }
        } else if (atendimento.getLembrete24hEm() == null && !agora.isBefore(marcoVespera)) {
            if (atendimento.getAceitoEm().isBefore(marcoVespera)) {
                notificador.lembrete(atendimento, Notificador.Lembrete.VESPERA);
            }
            atendimento.setLembrete24hEm(agora);
        }
    }

    private static void marcarLembretesEnviados(Atendimento atendimento, Instant agora) {
        if (atendimento.getLembrete24hEm() == null) {
            atendimento.setLembrete24hEm(agora);
        }
        if (atendimento.getLembrete30minEm() == null) {
            atendimento.setLembrete30minEm(agora);
        }
        if (atendimento.getLembreteInicioEm() == null) {
            atendimento.setLembreteInicioEm(agora);
        }
    }

    /**
     * A solicitação conclui sozinha quando nada mais vai acontecer: nenhum
     * atendimento ativo nem com problema em aberto, ao menos um encerrado
     * (realizado ou não) e, ou todas as vagas foram preenchidas, ou o prazo
     * desejado já passou. Pendentes que sobrarem são recusadas.
     */
    void concluirSolicitacaoSeTerminou(Solicitacao solicitacao) {
        if (!StatusSolicitacao.EM_CURSO.contains(solicitacao.getStatus())
                || atendimentoRepository.existsBySolicitacaoIdAndStatusIn(solicitacao.getId(), StatusAtendimento.ATIVOS)
                || atendimentoRepository.existsBySolicitacaoIdAndStatusIn(solicitacao.getId(), StatusAtendimento.EM_PROBLEMA)
                || !atendimentoRepository.existsBySolicitacaoIdAndStatusIn(solicitacao.getId(), StatusAtendimento.ENCERRADOS)) {
            return;
        }
        boolean prazoPassou = solicitacao.getPrazoDesejado().isBefore(LocalDate.now(clock.withZone(FUSO)));
        if (!solicitacao.todasAsVagasPreenchidas() && !prazoPassou) {
            return;
        }

        solicitacao.setStatus(StatusSolicitacao.CONCLUIDA);
        recusarPendentes(solicitacao);
    }

    private void recusarPendentes(Solicitacao solicitacao) {
        propostaRepository.findBySolicitacaoIdAndStatus(solicitacao.getId(), StatusProposta.PENDENTE).forEach(pendente -> {
            pendente.setStatus(StatusProposta.RECUSADA);
            notificador.propostaRecusada(pendente, true);
        });
    }

    // ============================================== problema relatado (4b)

    /**
     * Ficou decidido que não foi realizado (confirmado, sem resposta ou pelo
     * admin). Antes do término a vaga reabre; depois, não (o paciente pode
     * reabrir a solicitação).
     */
    @Transactional
    public void encerrarComoNaoRealizado(Atendimento atendimento, LadoAtendimento ausente, Instant agora) {
        atendimento.marcarNaoRealizado();
        Proposta proposta = atendimento.getProposta();
        if (agora.isBefore(atendimento.getTermino()) && proposta.getVaga() != null) {
            proposta.liberarVaga(false);
            proposta.setStatus(ausente == LadoAtendimento.PACIENTE ? StatusProposta.RECUSADA : StatusProposta.CANCELADA);
            recalcularSolicitacao(atendimento.getSolicitacao());
        }
        concluirSolicitacaoSeTerminou(atendimento.getSolicitacao());
    }

    /** O admin decidiu que aconteceu: volta para a vaga (se ainda houver) e segue o ciclo. */
    @Transactional
    public void retomarComoRealizado(Atendimento atendimento, Instant agora) {
        Proposta proposta = atendimento.getProposta();
        Solicitacao solicitacao = atendimento.getSolicitacao();
        if (proposta.getVaga() == null) {
            solicitacao.vagaDisponivelPara(proposta.getProfissional()).ifPresent(linha -> {
                linha.preencherVaga();
                proposta.setVaga(linha);
            });
        }
        proposta.setStatus(StatusProposta.ACEITA);
        atendimento.retomarComoRealizado(agora);
        recalcularSolicitacao(solicitacao);
        concluirSolicitacaoSeTerminou(solicitacao);
    }

    /** Evento de auditoria feito fora daqui (problema, admin). */
    @Transactional
    public void registrarEvento(Atendimento atendimento, TipoEventoAtendimento tipo, LadoAtendimento lado, String detalhe) {
        registrar(atendimento, tipo, lado, detalhe);
    }

    // ===================================================== ações do admin (4b)

    /**
     * O admin encerrou a solicitação (cancelada ou concluída): os atendimentos
     * que ainda iam acontecer caem sem ocorrência para ninguém e as pendentes
     * são recusadas. Devolve as propostas afetadas, para avisar.
     */
    @Transactional
    public List<Proposta> encerrarPeloAdmin(Solicitacao solicitacao, StatusSolicitacao novoStatus) {
        Instant agora = Instant.now(clock);
        List<Proposta> afetadas = new ArrayList<>();
        for (Atendimento atendimento : atendimentoRepository.findBySolicitacaoIdAndStatusIn(
                solicitacao.getId(), StatusAtendimento.ATIVOS)) {
            encerrar(atendimento, LadoAtendimento.SISTEMA, MotivoCancelamento.ADMIN, StatusProposta.CANCELADA, agora, false);
            afetadas.add(atendimento.getProposta());
        }
        for (Proposta pendente : propostaRepository.findBySolicitacaoIdAndStatus(solicitacao.getId(), StatusProposta.PENDENTE)) {
            pendente.setStatus(StatusProposta.RECUSADA);
            afetadas.add(pendente);
        }
        solicitacao.setStatus(novoStatus);
        return afetadas;
    }

    /** O admin reabriu: volta a ficar em curso, com o status calculado pelas vagas. */
    @Transactional
    public void reabrirPeloAdmin(Solicitacao solicitacao) {
        solicitacao.setStatus(StatusSolicitacao.ABERTA);
        recalcularSolicitacao(solicitacao);
    }

    // ================================================================= apoio

    /**
     * Encerra o atendimento ativo: sai da vaga (reservada ou não), a proposta
     * fica com o status dado e a solicitação recalcula o próprio status.
     */
    private void encerrar(Atendimento atendimento, LadoAtendimento lado, MotivoCancelamento motivo,
                          StatusProposta statusDaProposta, Instant agora, boolean manual) {
        boolean reservada = atendimento.getStatus() == StatusAtendimento.AGUARDANDO_REACEITE;
        atendimento.cancelar(lado, motivo, agora);

        Proposta proposta = atendimento.getProposta();
        proposta.liberarVaga(reservada);
        proposta.setStatus(statusDaProposta);
        registrar(atendimento, TipoEventoAtendimento.CANCELADO, lado, manual ? null : motivo.name());

        recalcularSolicitacao(atendimento.getSolicitacao());
    }

    /**
     * Sem vaga livre, toma a reservada mais antiga de um profissional que ainda
     * não reaceitou. Se a edição ou a troca é a menos de 48h do atendimento,
     * conta ocorrência para o paciente.
     */
    private SolicitacaoProfissional tomarVagaReservada(Proposta proposta, Instant agora) {
        Solicitacao solicitacao = proposta.getSolicitacao();
        Set<SolicitacaoProfissional> linhas = Set.copyOf(solicitacao.linhasQueRecebem(proposta.getProfissional()).toList());

        Atendimento substituido = atendimentoRepository.findBySolicitacaoIdAndStatusIn(
                        solicitacao.getId(), Set.of(StatusAtendimento.AGUARDANDO_REACEITE)).stream()
                .filter(atendimento -> linhas.contains(atendimento.getProposta().getVaga()))
                .min(Comparator.comparing(Atendimento::getReaceiteSolicitadoEm))
                .orElseThrow(() -> new TransicaoStatusInvalidaException(
                        "Não há mais vagas abertas para o perfil deste profissional."));

        SolicitacaoProfissional linha = substituido.getProposta().getVaga();
        boolean tardia = RegrasOcorrencia.alteracaoTardia(substituido, agora);
        encerrar(substituido, LadoAtendimento.PACIENTE, MotivoCancelamento.SUBSTITUIDO, StatusProposta.RECUSADA, agora, false);
        if (tardia) {
            ocorrenciaDoPaciente(substituido, MotivoOcorrencia.ALTERACAO_TARDIA_SOLICITACAO);
        }
        notificador.substituido(substituido.getProposta());
        return linha;
    }

    /** Pendentes (fora a recém-aceita) que não cabem em vaga livre nem reservada são recusadas. */
    private void recusarPendentesSemVaga(Solicitacao solicitacao, Proposta aceita) {
        propostaRepository.findBySolicitacaoIdAndStatus(solicitacao.getId(), StatusProposta.PENDENTE).stream()
                .filter(pendente -> aceita == null || !pendente.getId().equals(aceita.getId()))
                .filter(pendente -> solicitacao.linhasQueRecebem(pendente.getProfissional()).findAny().isEmpty())
                .forEach(pendente -> {
                    pendente.setStatus(StatusProposta.RECUSADA);
                    notificador.propostaRecusada(pendente, true);
                });
    }

    private void recalcularSolicitacao(Solicitacao solicitacao) {
        solicitacao.recalcularStatus(
                propostaRepository.existsBySolicitacaoIdAndStatus(solicitacao.getId(), StatusProposta.PENDENTE));
    }

    private void ocorrenciaDoPaciente(Atendimento atendimento, MotivoOcorrencia motivo) {
        ocorrenciaService.registrar(usuarioDo(atendimento, LadoAtendimento.PACIENTE), TipoPerfil.PACIENTE, motivo, atendimento);
    }

    private void registrar(Atendimento atendimento, TipoEventoAtendimento tipo, LadoAtendimento lado, String detalhe) {
        eventoRepository.save(AtendimentoEvento.builder()
                .atendimento(atendimento)
                .tipo(tipo)
                .autor(lado == LadoAtendimento.SISTEMA ? null : usuarioDo(atendimento, lado))
                .inicio(atendimento.getInicio())
                .termino(atendimento.getTermino())
                .detalhe(detalhe)
                .build());
    }

    private static Usuario usuarioDo(Atendimento atendimento, LadoAtendimento lado) {
        return lado == LadoAtendimento.PACIENTE
                ? atendimento.getSolicitacao().getPaciente().getUsuario()
                : atendimento.getProposta().getProfissional().getUsuario();
    }

    private static TipoPerfil perfilDo(LadoAtendimento lado) {
        return lado == LadoAtendimento.PACIENTE ? TipoPerfil.PACIENTE : TipoPerfil.PROFISSIONAL;
    }

    private Atendimento buscarDoUsuario(Long usuarioId, Long id) {
        Atendimento atendimento = atendimentoRepository.findWithPartesById(id)
                .filter(encontrado -> encontrado.ladoDe(usuarioId).isPresent())
                .orElseThrow(AtendimentoNaoEncontradoException::new);

        boolean banido = ladoDe(atendimento, usuarioId) == LadoAtendimento.PACIENTE
                ? atendimento.getSolicitacao().getPaciente().isBanido()
                : atendimento.getProposta().getProfissional().isBanido();
        if (banido) {
            throw new PerfilNaoAutorizadoException("Seu perfil não pode alterar atendimentos.");
        }
        return atendimento;
    }

    private Atendimento buscarDoLado(Long usuarioId, Long id, LadoAtendimento lado) {
        Atendimento atendimento = buscarDoUsuario(usuarioId, id);
        if (ladoDe(atendimento, usuarioId) != lado) {
            throw new PerfilNaoAutorizadoException(lado == LadoAtendimento.PROFISSIONAL
                    ? "Só o profissional pode reaceitar a solicitação."
                    : "Só o paciente pode fazer isso.");
        }
        return atendimento;
    }

    private static LadoAtendimento ladoDe(Atendimento atendimento, Long usuarioId) {
        return atendimento.ladoDe(usuarioId).orElseThrow(AtendimentoNaoEncontradoException::new);
    }

    private AtendimentoResponse responder(Atendimento atendimento) {
        return AtendimentoResponse.de(atendimento, Instant.now(clock));
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new TransicaoStatusInvalidaException(mensagem);
        }
    }

}
