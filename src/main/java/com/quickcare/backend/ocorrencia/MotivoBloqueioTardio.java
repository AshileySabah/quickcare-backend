package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.usuario.TipoPerfil;
import com.quickcare.backend.usuario.Usuario;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "motivo_bloqueio_tardio")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MotivoBloqueioTardio {

    public enum Status {
        EM_ANALISE,
        ACEITO,
        NEGADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPerfil perfil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendimento_id", nullable = false)
    private Atendimento atendimento;

    @Column(nullable = false, length = 1000)
    private String texto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ocorrencia_id")
    private Ocorrencia ocorrencia;

    @Column(name = "decidido_em")
    private Instant decididoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidido_por_usuario_id")
    private Usuario decididoPor;

    @Column(name = "justificativa_decisao", length = 1000)
    private String justificativaDecisao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public boolean emAnalise() {
        return status == Status.EM_ANALISE;
    }

    public void decidir(Usuario admin, boolean aceitar, String justificativa, Instant agora) {
        status = aceitar ? Status.ACEITO : Status.NEGADO;
        decididoPor = admin;
        decididoEm = agora;
        justificativaDecisao = justificativa.trim();
    }

    public void gerou(Ocorrencia ocorrencia) {
        this.ocorrencia = ocorrencia;
    }

}
