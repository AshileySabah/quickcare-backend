package com.quickcare.backend.proposta;

import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.solicitacao.Solicitacao;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "proposta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Proposta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitacao_id", nullable = false)
    private Solicitacao solicitacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atuacao_id", nullable = false)
    private Atuacao atuacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitacao_profissional_id")
    private SolicitacaoProfissional vaga;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 1000)
    private String abordagem;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant termino;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusProposta status;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_reaceite", length = 30)
    private MotivoReaceite motivoReaceite;

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

    public void voltarParaReaceite(MotivoReaceite motivo) {
        status = StatusProposta.PENDENTE;
        motivoReaceite = motivo;
    }

    public void liberarVaga(boolean reservada) {
        if (vaga == null) {
            return;
        }
        if (reservada) {
            vaga.liberarVagaReservada();
        } else {
            vaga.liberarVaga();
        }
        vaga = null;
    }

    public boolean ehDoUsuario(Long usuarioId) {
        return profissional.getUsuario().getId().equals(usuarioId);
    }

}
