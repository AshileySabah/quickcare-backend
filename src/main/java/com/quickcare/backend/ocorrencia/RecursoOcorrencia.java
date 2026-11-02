package com.quickcare.backend.ocorrencia;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** O usuário discorda de uma ocorrência e explica; o admin mantém ou anula, com justificativa. */
@Entity
@Table(name = "recurso_ocorrencia")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecursoOcorrencia {

    public enum Status {
        EM_ANALISE,
        MANTIDA,
        ANULADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ocorrencia_id", nullable = false, unique = true)
    private Ocorrencia ocorrencia;

    @Column(nullable = false, length = 1000)
    private String texto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

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

    public void decidir(Usuario admin, boolean anular, String justificativa, Instant agora) {
        status = anular ? Status.ANULADA : Status.MANTIDA;
        decididoPor = admin;
        decididoEm = agora;
        justificativaDecisao = justificativa.trim();
    }

}
