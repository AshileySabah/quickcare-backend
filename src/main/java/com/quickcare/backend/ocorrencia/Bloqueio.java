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

import java.time.Duration;
import java.time.Instant;

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

    @Column(nullable = false)
    private boolean desfeito;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_usuario_id")
    private Usuario criadoPor;

    @Column(length = 1000)
    private String justificativa;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public boolean aplicadoPeloAdmin() {
        return criadoPor != null;
    }

    public boolean ativoEm(Instant agora) {
        return removidoEm == null && !agora.isBefore(inicio) && agora.isBefore(fim);
    }

    public Instant terminoEfetivo() {
        return removidoEm != null && removidoEm.isBefore(fim) ? removidoEm : fim;
    }

    public void prorrogarPorCongelamento(Instant congeladoEm, Duration duracao) {
        if (removidoEm == null && fim.isAfter(congeladoEm)) {
            fim = fim.plus(duracao);
        }
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
