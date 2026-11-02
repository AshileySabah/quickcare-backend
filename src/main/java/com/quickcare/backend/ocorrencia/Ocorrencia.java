package com.quickcare.backend.ocorrencia;

import com.quickcare.backend.atendimento.Atendimento;
import com.quickcare.backend.problema.ProblemaAtendimento;
import com.quickcare.backend.solicitacao.Solicitacao;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Uma falta que conta contra o perfil (3 em 90 dias bloqueiam). O admin pode
 * lançar uma manualmente ou anular, sempre com justificativa; anulada, deixa
 * de contar. O usuário pode recorrer uma vez ({@link RecursoOcorrencia}).
 */
@Entity
@Table(name = "ocorrencia")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ocorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPerfil perfil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MotivoOcorrencia motivo;

    /** Nula na lançada manualmente pelo admin. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitacao_id")
    private Solicitacao solicitacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendimento_id")
    private Atendimento atendimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problema_id")
    private ProblemaAtendimento problema;

    /** Do admin, quando foi ele quem lançou. */
    @Column(length = 1000)
    private String justificativa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_usuario_id")
    private Usuario criadoPor;

    @Column(name = "anulada_em")
    private Instant anuladaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulada_por_usuario_id")
    private Usuario anuladaPor;

    @Column(name = "justificativa_anulacao", length = 1000)
    private String justificativaAnulacao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @OneToOne(mappedBy = "ocorrencia", fetch = FetchType.LAZY)
    private RecursoOcorrencia recurso;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public boolean anulada() {
        return anuladaEm != null;
    }

    /** Uma vez só, enquanto vale e se o motivo admite ({@link MotivoOcorrencia#recorrivel}). */
    public boolean podeRecorrer() {
        return !anulada() && recurso == null && motivo.recorrivel();
    }

    void setRecurso(RecursoOcorrencia recurso) {
        this.recurso = recurso;
    }

    public void anular(Usuario admin, String justificativa, Instant agora) {
        anuladaEm = agora;
        anuladaPor = admin;
        justificativaAnulacao = justificativa.trim();
    }

}
