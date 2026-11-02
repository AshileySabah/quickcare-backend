package com.quickcare.backend.problema;

import com.quickcare.backend.administrador.AcaoAdmin;
import com.quickcare.backend.administrador.AcessoAdmin;
import com.quickcare.backend.administrador.TipoAcaoAdmin;
import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.AtendimentoRepository;
import com.quickcare.backend.atendimento.AtendimentoService;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.atendimento.TipoEventoAtendimento;
import com.quickcare.backend.atendimento.dto.AtendimentoResponse;
import com.quickcare.backend.atendimento.exception.AtendimentoNaoEncontradoException;
import com.quickcare.backend.common.RecursoNaoEncontradoException;
import com.quickcare.backend.documento.ArmazenamentoDocumentoService;
import com.quickcare.backend.documento.ArquivoInvalidoException;
import com.quickcare.backend.notificacao.Notificador;
import com.quickcare.backend.ocorrencia.MotivoOcorrencia;
import com.quickcare.backend.ocorrencia.OcorrenciaService;
import com.quickcare.backend.problema.dto.ContestacaoRequest;
import com.quickcare.backend.problema.dto.DecisaoDisputaRequest;
import com.quickcare.backend.problema.dto.RelatoRequest;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * "Não foi realizado": um lado relata (com até 3 anexos), o outro confirma ou
 * contesta em 7 dias; sem resposta, vale o relato e quem não compareceu leva
 * ocorrência. Contestado (inclusive depois do prazo), o admin decide.
 */
@Slf4j
@Service
public class ProblemaService {

    private final ProblemaRepository problemaRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final AtendimentoService atendimentoService;
    private final OcorrenciaService ocorrenciaService;
    private final ArmazenamentoDocumentoService armazenamento;
    private final Notificador notificador;
    private final AcessoAdmin acessoAdmin;
    private final Clock clock;
    private final TransactionTemplate transacao;

    public ProblemaService(
            ProblemaRepository problemaRepository,
            AtendimentoRepository atendimentoRepository,
            AtendimentoService atendimentoService,
            OcorrenciaService ocorrenciaService,
            ArmazenamentoDocumentoService armazenamento,
            Notificador notificador,
            AcessoAdmin acessoAdmin,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.problemaRepository = problemaRepository;
        this.atendimentoRepository = atendimentoRepository;
        this.atendimentoService = atendimentoService;
        this.ocorrenciaService = ocorrenciaService;
        this.armazenamento = armazenamento;
        this.notificador = notificador;
        this.acessoAdmin = acessoAdmin;
        this.clock = clock;
        this.transacao = new TransactionTemplate(transactionManager);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // =================================================== as partes do atendimento

    @Transactional
    public AtendimentoResponse relatar(Long usuarioId, Long atendimentoId, RelatoRequest request, List<MultipartFile> anexos) {
        Atendimento atendimento = buscarDoUsuario(usuarioId, atendimentoId);
        LadoAtendimento lado = ladoDe(atendimento, usuarioId);
        Instant agora = Instant.now(clock);
        validarAnexos(anexos);
        if (problemaRepository.existsByAtendimentoId(atendimentoId)) {
            throw new TransicaoStatusInvalidaException(
                    "Já existe um relato de problema para este atendimento.");
        }

        ProblemaAtendimento problema = ProblemaAtendimento.relatar(atendimento, lado, request.motivo(), request.descricao(), agora);
        atendimento.relatarProblema(agora, AtendimentoService.JANELA_CONCLUSAO);
        anexar(problema, lado, anexos);
        problemaRepository.save(problema);
        atendimento.setProblema(problema);

        atendimentoService.registrarEvento(atendimento, TipoEventoAtendimento.PROBLEMA_RELATADO, lado,
                request.motivo().name());
        notificador.problemaRelatado(problema);
        return responder(atendimento);
    }

    @Transactional
    public AtendimentoResponse confirmar(Long usuarioId, Long atendimentoId) {
        Atendimento atendimento = buscarDoUsuario(usuarioId, atendimentoId);
        LadoAtendimento lado = ladoDe(atendimento, usuarioId);
        ProblemaAtendimento problema = problemaDo(atendimento);
        Instant agora = Instant.now(clock);

        problema.confirmar(lado, agora);
        encerrarComoNaoRealizado(problema, agora);
        atendimentoService.registrarEvento(atendimento, TipoEventoAtendimento.PROBLEMA_CONFIRMADO, lado, null);
        notificador.problemaConfirmado(problema);
        return responder(atendimento);
    }

    /** Antes ou depois do prazo de 7 dias: vira disputa e o admin decide. */
    @Transactional
    public AtendimentoResponse contestar(Long usuarioId, Long atendimentoId, ContestacaoRequest request,
                                         List<MultipartFile> anexos) {
        Atendimento atendimento = buscarDoUsuario(usuarioId, atendimentoId);
        LadoAtendimento lado = ladoDe(atendimento, usuarioId);
        ProblemaAtendimento problema = problemaDo(atendimento);
        validarAnexos(anexos);

        problema.contestar(lado, request.descricao(), Instant.now(clock));
        atendimento.entrarEmDisputa();
        anexar(problema, lado, anexos);
        atendimentoService.registrarEvento(atendimento, TipoEventoAtendimento.PROBLEMA_CONTESTADO, lado, null);
        notificador.problemaContestado(problema);
        return responder(atendimento);
    }

    /** URL temporária de um anexo, para as partes do atendimento. */
    @Transactional(readOnly = true)
    public String urlDoAnexo(Long usuarioId, Long atendimentoId, Long anexoId) {
        Atendimento atendimento = atendimentoRepository.findWithPartesById(atendimentoId)
                .filter(encontrado -> encontrado.ladoDe(usuarioId).isPresent())
                .orElseThrow(AtendimentoNaoEncontradoException::new);
        return urlDoAnexo(problemaDo(atendimento), anexoId);
    }

    // ============================================================= prazo

    /** Relatos sem resposta em 7 dias passam a valer (job a cada minuto). */
    public void processarVencidos() {
        Instant agora = Instant.now(clock);
        for (Long id : problemaRepository.findIdsComPrazoVencido(agora)) {
            try {
                transacao.executeWithoutResult(status -> expirar(id, agora));
            } catch (RuntimeException ex) {
                log.warn("Não foi possível encerrar o relato {}: {}", id, ex.getMessage());
            }
        }
    }

    private void expirar(Long id, Instant agora) {
        ProblemaAtendimento problema = problemaRepository.findWithPartesById(id).orElse(null);
        if (problema == null || !problema.prazoVenceu(agora)) {
            return;
        }
        problema.expirar();
        encerrarComoNaoRealizado(problema, agora);
        atendimentoService.registrarEvento(problema.getAtendimento(), TipoEventoAtendimento.PROBLEMA_SEM_RESPOSTA,
                LadoAtendimento.SISTEMA, null);
        notificador.problemaSemResposta(problema);
    }

    // ============================================================= admin

    /**
     * O admin decide se aconteceu e quem leva ocorrência; as ocorrências que o
     * relato tinha gerado são anuladas e trocadas pelas da decisão.
     */
    @Transactional
    public ProblemaAtendimento decidir(Long adminUsuarioId, Long problemaId, DecisaoDisputaRequest request) {
        Usuario admin = acessoAdmin.exigir(adminUsuarioId);
        ProblemaAtendimento problema = buscar(problemaId);
        Atendimento atendimento = problema.getAtendimento();
        Instant agora = Instant.now(clock);

        problema.decidir(request.decisao(), request.justificativa(), admin, agora);
        ocorrenciaService.anularDoProblema(admin, problema, "Substituída pela decisão da disputa.");
        if (request.decisao() == DecisaoProblema.REALIZADO) {
            atendimentoService.retomarComoRealizado(atendimento, agora);
        } else {
            atendimentoService.encerrarComoNaoRealizado(atendimento, problema.ausente(), agora);
        }
        for (LadoAtendimento lado : request.ocorrenciaParaOuNenhum()) {
            if (lado != LadoAtendimento.SISTEMA) {
                ocorrenciaService.registrar(usuarioDo(atendimento, lado), perfilDo(lado), MotivoOcorrencia.DECISAO_DISPUTA,
                        atendimento, problema);
            }
        }

        atendimentoService.registrarEvento(atendimento, TipoEventoAtendimento.DISPUTA_DECIDIDA, LadoAtendimento.SISTEMA,
                request.decisao().name());
        acessoAdmin.registrar(admin, TipoAcaoAdmin.DISPUTA_DECIDIDA, AcaoAdmin.Alvo.PROBLEMA, problema.getId(),
                request.decisao().name() + (request.ocorrenciaParaOuNenhum().isEmpty() ? ""
                        : "; ocorrência para " + request.ocorrenciaParaOuNenhum()), request.justificativa());
        notificador.disputaDecidida(problema);
        return problema;
    }

    @Transactional(readOnly = true)
    public ProblemaAtendimento buscar(Long problemaId) {
        return problemaRepository.findWithPartesById(problemaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Disputa não encontrada."));
    }

    public String urlDoAnexo(ProblemaAtendimento problema, Long anexoId) {
        return problema.getAnexos().stream()
                .filter(anexo -> anexo.getId().equals(anexoId))
                .findFirst()
                .map(anexo -> armazenamento.gerarUrlAssinada(anexo.getStorageKey()))
                .filter(Objects::nonNull)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Anexo não encontrado."));
    }

    // ============================================================= apoio

    /** Vale o relato: não realizado e, se alguém faltou, ocorrência para ele. */
    private void encerrarComoNaoRealizado(ProblemaAtendimento problema, Instant agora) {
        Atendimento atendimento = problema.getAtendimento();
        LadoAtendimento ausente = problema.ausente();
        atendimentoService.encerrarComoNaoRealizado(atendimento, ausente, agora);
        if (ausente != null) {
            ocorrenciaService.registrar(usuarioDo(atendimento, ausente), perfilDo(ausente),
                    MotivoOcorrencia.NAO_COMPARECIMENTO, atendimento, problema);
        }
    }

    private void validarAnexos(List<MultipartFile> anexos) {
        List<MultipartFile> enviados = enviados(anexos);
        if (enviados.size() > ProblemaAtendimento.MAXIMO_ANEXOS_POR_LADO) {
            throw new ArquivoInvalidoException("Anexe no máximo " + ProblemaAtendimento.MAXIMO_ANEXOS_POR_LADO + " arquivos.");
        }
        enviados.forEach(armazenamento::validarAnexo);
    }

    private void anexar(ProblemaAtendimento problema, LadoAtendimento lado, List<MultipartFile> anexos) {
        for (MultipartFile arquivo : enviados(anexos)) {
            problema.anexar(ProblemaAnexo.builder()
                    .lado(lado)
                    .nomeArquivo(arquivo.getOriginalFilename() == null ? "anexo" : arquivo.getOriginalFilename())
                    .contentType(arquivo.getContentType())
                    .tamanhoBytes(arquivo.getSize())
                    .storageKey(armazenamento.enviarAnexoProblema(arquivo))
                    .build());
        }
    }

    private static List<MultipartFile> enviados(List<MultipartFile> anexos) {
        return anexos == null ? List.of() : anexos.stream().filter(arquivo -> arquivo != null && !arquivo.isEmpty()).toList();
    }

    private ProblemaAtendimento problemaDo(Atendimento atendimento) {
        return problemaRepository.findByAtendimentoId(atendimento.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Não há relato de problema neste atendimento."));
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

    private static LadoAtendimento ladoDe(Atendimento atendimento, Long usuarioId) {
        return atendimento.ladoDe(usuarioId).orElseThrow(AtendimentoNaoEncontradoException::new);
    }

    private static Usuario usuarioDo(Atendimento atendimento, LadoAtendimento lado) {
        return lado == LadoAtendimento.PACIENTE
                ? atendimento.getSolicitacao().getPaciente().getUsuario()
                : atendimento.getProposta().getProfissional().getUsuario();
    }

    private static TipoPerfil perfilDo(LadoAtendimento lado) {
        return lado == LadoAtendimento.PACIENTE ? TipoPerfil.PACIENTE : TipoPerfil.PROFISSIONAL;
    }

    private AtendimentoResponse responder(Atendimento atendimento) {
        return AtendimentoResponse.de(atendimento, Instant.now(clock));
    }

}
