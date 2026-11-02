package com.quickcare.backend.atendimento;

import com.quickcare.backend.proposta.Proposta;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Optional;

/**
 * O atendimento de uma proposta aceita: quando acontece e em que pé está. As
 * datas começam iguais às da proposta e mudam por remarcação aceita.
 */
@Entity
@Table(name = "atendimento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Atendimento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposta_id", nullable = false)
    private Proposta proposta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitacao_id", nullable = false)
    private Solicitacao solicitacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusAtendimento status;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant termino;

    /** Última aceitação (aceite, reaceite ou remarcação aceita): base da regra das 48h. */
    @Column(name = "aceito_em", nullable = false)
    private Instant aceitoEm;

    /** Quando o paciente editou a solicitação e pediu o reaceite. */
    @Column(name = "reaceite_solicitado_em")
    private Instant reaceiteSolicitadoEm;

    @Column(name = "remarcacao_inicio")
    private Instant remarcacaoInicio;

    @Column(name = "remarcacao_termino")
    private Instant remarcacaoTermino;

    @Enumerated(EnumType.STRING)
    @Column(name = "remarcacao_por", length = 20)
    private LadoAtendimento remarcacaoPor;

    @Column(name = "remarcacao_solicitada_em")
    private Instant remarcacaoSolicitadaEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelado_por", length = 20)
    private LadoAtendimento canceladoPor;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_cancelamento", length = 40)
    private MotivoCancelamento motivoCancelamento;

    @Column(name = "cancelado_em")
    private Instant canceladoEm;

    @Column(name = "lembrete_24h_em")
    private Instant lembrete24hEm;

    @Column(name = "lembrete_30min_em")
    private Instant lembrete30minEm;

    @Column(name = "lembrete_inicio_em")
    private Instant lembreteInicioEm;

    @Version
    @Column(nullable = false)
    private long versao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    private void aoPersistir() {
        Instant agora = Instant.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        atualizadoEm = agora;
    }

    @PreUpdate
    private void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    /** O paciente aceitou a proposta: o atendimento fica marcado nas datas dela. */
    public static Atendimento agendar(Proposta proposta, Instant agora) {
        return Atendimento.builder()
                .proposta(proposta)
                .solicitacao(proposta.getSolicitacao())
                .status(StatusAtendimento.AGENDADO)
                .inicio(proposta.getInicio())
                .termino(proposta.getTermino())
                .aceitoEm(agora)
                .build();
    }

    /** De que lado o usuário está neste atendimento, se estiver. */
    public Optional<LadoAtendimento> ladoDe(Long usuarioId) {
        if (solicitacao.ehDoUsuario(usuarioId)) {
            return Optional.of(LadoAtendimento.PACIENTE);
        }
        if (proposta.ehDoUsuario(usuarioId)) {
            return Optional.of(LadoAtendimento.PROFISSIONAL);
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------ reaceite

    public void solicitarReaceite(Instant agora) {
        exigir(status == StatusAtendimento.AGENDADO || status == StatusAtendimento.AGUARDANDO_REACEITE,
                "Este atendimento não aguarda mais reaceite.");
        status = StatusAtendimento.AGUARDANDO_REACEITE;
        reaceiteSolicitadoEm = agora;
        limparRemarcacao();
    }

    public void reaceitar(Instant agora) {
        exigir(status == StatusAtendimento.AGUARDANDO_REACEITE, "Este atendimento não aguarda reaceite.");
        exigir(agora.isBefore(inicio), "O horário de início já passou.");
        status = StatusAtendimento.AGENDADO;
        aceitoEm = agora;
    }

    // ---------------------------------------------------------- remarcação

    /**
     * Antes do início, propõe outro período; em andamento, só outro término. O
     * outro lado aceita ou recusa (a recusa mantém o período atual).
     */
    public void solicitarRemarcacao(LadoAtendimento lado, Instant novoInicio, Instant novoTermino, Instant agora) {
        exigir(status.podeSerAlterado(), "Este atendimento não pode mais ser remarcado.");
        exigir(remarcacaoPor == null || remarcacaoPor == lado,
                "Há uma remarcação pedida pelo outro lado. Responda a ela antes de pedir outra.");
        exigir(novoTermino.isAfter(novoInicio), "O término precisa ser depois do início.");
        if (status == StatusAtendimento.EM_ANDAMENTO) {
            exigir(novoInicio.equals(inicio), "O atendimento já começou: só o término pode mudar.");
            exigir(novoTermino.isAfter(agora), "O novo término precisa estar no futuro.");
        } else {
            exigir(novoInicio.isAfter(agora), "O novo início precisa estar no futuro.");
        }
        exigir(!novoInicio.equals(inicio) || !novoTermino.equals(termino), "Escolha um período diferente do atual.");

        remarcacaoInicio = novoInicio;
        remarcacaoTermino = novoTermino;
        remarcacaoPor = lado;
        remarcacaoSolicitadaEm = agora;
    }

    public boolean temRemarcacaoPendente() {
        return remarcacaoPor != null;
    }

    /** Só o outro lado responde; aceitar conta como nova aceitação (48h) e refaz os lembretes. */
    public void aceitarRemarcacao(LadoAtendimento quemResponde, Instant agora) {
        exigirRemarcacaoDoOutroLado(quemResponde);
        exigir(status != StatusAtendimento.AGENDADO || remarcacaoInicio.isAfter(agora),
                "O início proposto já passou. Peça uma nova remarcação.");
        inicio = remarcacaoInicio;
        termino = remarcacaoTermino;
        aceitoEm = agora;
        lembrete24hEm = null;
        lembrete30minEm = null;
        lembreteInicioEm = null;
        limparRemarcacao();
    }

    public void recusarRemarcacao(LadoAtendimento quemResponde) {
        exigirRemarcacaoDoOutroLado(quemResponde);
        limparRemarcacao();
    }

    public void limparRemarcacao() {
        remarcacaoInicio = null;
        remarcacaoTermino = null;
        remarcacaoPor = null;
        remarcacaoSolicitadaEm = null;
    }

    // ------------------------------------------------------- ciclo de vida

    public void cancelar(LadoAtendimento lado, MotivoCancelamento motivo, Instant agora) {
        exigir(status.ativo(), "Este atendimento já foi encerrado.");
        status = StatusAtendimento.CANCELADO;
        canceladoPor = lado;
        motivoCancelamento = motivo;
        canceladoEm = agora;
        limparRemarcacao();
    }

    public void iniciar() {
        status = StatusAtendimento.EM_ANDAMENTO;
        if (remarcacaoPor != null && remarcacaoInicio != null && !remarcacaoInicio.equals(inicio)) {
            // Uma remarcação do início não respondida a tempo deixa de valer.
            limparRemarcacao();
        }
    }

    public void finalizar() {
        status = StatusAtendimento.FINALIZADO;
        limparRemarcacao();
    }

    public void concluir() {
        status = StatusAtendimento.CONCLUIDO;
    }

    private void exigirRemarcacaoDoOutroLado(LadoAtendimento quemResponde) {
        exigir(remarcacaoPor != null, "Não há remarcação pendente.");
        exigir(remarcacaoPor != quemResponde, "Quem responde a remarcação é o outro lado.");
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new TransicaoStatusInvalidaException(mensagem);
        }
    }

}
