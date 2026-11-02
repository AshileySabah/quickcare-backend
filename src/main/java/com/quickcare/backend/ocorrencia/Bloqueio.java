package com.quickcare.backend.ocorrencia;

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

/**
 * O perfil não cria solicitação (paciente) nem envia proposta (profissional)
 * até {@code fim}; o resto segue (editar, aceitar, atender). O admin pode
 * remover antes, com justificativa.
 */
@Entity
@Table(name = "bloqueio")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bloqueio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPerfil perfil;

    /** 1º bloqueio = 7 dias, 2º = 14, 3º = 21...; volta a 1 depois de 60 dias sem ocorrência. */
    @Column(nullable = false)
    private int nivel;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant fim;

    @Column(name = "removido_em")
    private Instant removidoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "removido_por_usuario_id")
    private Usuario removidoPor;

    @Column(name = "justificativa_remocao", length = 1000)
    private String justificativaRemocao;

    /**
     * Anularam uma das ocorrências que o causaram e as que sobraram não chegam
     * ao limite: deixa de valer e conta como se não tivesse existido (não zera
     * o contador nem entra no nível do próximo).
     */
    @Column(nullable = false)
    private boolean desfeito;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public boolean ativoEm(Instant agora) {
        return removidoEm == null && !agora.isBefore(inicio) && agora.isBefore(fim);
    }

    /** Quando deixou de valer: no fim previsto ou na remoção pelo admin. */
    public Instant terminoEfetivo() {
        return removidoEm != null && removidoEm.isBefore(fim) ? removidoEm : fim;
    }

    public void desfazer(Instant agora) {
        removidoEm = agora;
        desfeito = true;
        justificativaRemocao = "Uma das ocorrências que causaram o bloqueio foi anulada.";
    }

    public void remover(Usuario admin, String justificativa, Instant agora) {
        removidoEm = agora;
        removidoPor = admin;
        justificativaRemocao = justificativa.trim();
    }

}
