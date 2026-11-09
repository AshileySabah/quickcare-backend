package com.quickcare.backend.seguranca;

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
@Table(name = "codigo_seguranca")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodigoSeguranca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FinalidadeCodigo finalidade;

    @Column(name = "codigo_hash", nullable = false, length = 64)
    private String codigoHash;

    private String dado;

    @Column(name = "senha_hash_anterior", length = 100)
    private String senhaHashAnterior;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public boolean expirado(Instant agora) {
        return !expiraEm.isAfter(agora);
    }

    public int registrarTentativa() {
        return ++tentativas;
    }

}
