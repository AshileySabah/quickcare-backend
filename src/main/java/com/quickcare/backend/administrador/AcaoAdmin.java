package com.quickcare.backend.administrador;

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
@Table(name = "acao_admin")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcaoAdmin {

    public enum Alvo {
        OCORRENCIA,
        BLOQUEIO,
        PROBLEMA,
        SOLICITACAO,
        PROPOSTA,
        BLOQUEIO_USUARIO,
        USUARIO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_usuario_id")
    private Usuario admin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoAcaoAdmin tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "alvo_tipo", nullable = false, length = 30)
    private Alvo alvoTipo;

    @Column(name = "alvo_id", nullable = false)
    private Long alvoId;

    @Column(length = 500)
    private String detalhe;

    @Column(nullable = false, length = 1000)
    private String justificativa;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

}
