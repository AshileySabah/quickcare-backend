package com.quickcare.backend.problema;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.atendimento.StatusAtendimento;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
import com.quickcare.backend.usuario.Usuario;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "problema_atendimento")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemaAtendimento {

    public static final Duration PRAZO_RESPOSTA = Duration.ofDays(7);
    public static final int MAXIMO_ANEXOS_POR_LADO = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendimento_id", nullable = false, unique = true)
    private Atendimento atendimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "relatado_por", nullable = false, length = 20)
    private LadoAtendimento relatadoPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MotivoProblema motivo;

    @Column(nullable = false, length = 2000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusProblema status;

    @Column(name = "prazo_resposta", nullable = false)
    private Instant prazoResposta;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_anterior", nullable = false, length = 30)
    private StatusAtendimento statusAnterior;

    @Column(name = "respondido_em")
    private Instant respondidoEm;

    @Column(length = 2000)
    private String contestacao;

    @Column(name = "contestado_em")
    private Instant contestadoEm;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DecisaoProblema decisao;

    @Column(name = "decisao_justificativa", length = 2000)
    private String decisaoJustificativa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidido_por_usuario_id")
    private Usuario decididoPor;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    @Builder.Default
    @OneToMany(mappedBy = "problema", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ProblemaAnexo> anexos = new ArrayList<>();

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

    public static ProblemaAtendimento relatar(
            Atendimento atendimento, LadoAtendimento lado, MotivoProblema motivo, String descricao, Instant agora) {
        return ProblemaAtendimento.builder()
                .atendimento(atendimento)
                .relatadoPor(lado)
                .motivo(motivo)
                .descricao(descricao.trim())
                .status(StatusProblema.AGUARDANDO_RESPOSTA)
                .prazoResposta(agora.plus(PRAZO_RESPOSTA))
                .statusAnterior(atendimento.getStatus())
                .build();
    }

    public LadoAtendimento ladoQueResponde() {
        return relatadoPor.outro();
    }

    public LadoAtendimento ausente() {
        return motivo == MotivoProblema.OUTRO_NAO_COMPARECEU ? relatadoPor.outro() : null;
    }

    public void anexar(ProblemaAnexo anexo) {
        anexo.setProblema(this);
        anexos.add(anexo);
    }

    public List<ProblemaAnexo> anexosDo(LadoAtendimento lado) {
        return anexos.stream().filter(anexo -> anexo.getLado() == lado).toList();
    }

    public void confirmar(LadoAtendimento quem, Instant agora) {
        exigirQueResponda(quem);
        exigir(status == StatusProblema.AGUARDANDO_RESPOSTA, "Este relato não aguarda mais resposta.");
        status = StatusProblema.CONFIRMADO;
        respondidoEm = agora;
    }

    public void contestar(LadoAtendimento quem, String texto, Instant agora) {
        exigirQueResponda(quem);
        exigir(StatusProblema.CONTESTAVEIS.contains(status), "Este relato não pode mais ser contestado.");
        status = StatusProblema.EM_DISPUTA;
        contestacao = texto.trim();
        contestadoEm = agora;
        if (respondidoEm == null) {
            respondidoEm = agora;
        }
    }

    public boolean prazoVenceu(Instant agora) {
        return status == StatusProblema.AGUARDANDO_RESPOSTA && !agora.isBefore(prazoResposta);
    }

    public void prorrogarPorCongelamento(Duration duracao) {
        if (status == StatusProblema.AGUARDANDO_RESPOSTA) {
            prazoResposta = prazoResposta.plus(duracao);
        }
    }

    public void expirar() {
        status = StatusProblema.SEM_RESPOSTA;
    }

    public void decidir(DecisaoProblema resultado, String justificativa, Usuario admin, Instant agora) {
        exigir(status == StatusProblema.EM_DISPUTA, "Só disputas em aberto podem ser decididas.");
        status = StatusProblema.DECIDIDO;
        decisao = resultado;
        decisaoJustificativa = justificativa.trim();
        decididoPor = admin;
        decididoEm = agora;
    }

    private void exigirQueResponda(LadoAtendimento quem) {
        exigir(quem == ladoQueResponde(), "Quem responde ao relato é o outro lado do atendimento.");
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new TransicaoStatusInvalidaException(mensagem);
        }
    }

}
