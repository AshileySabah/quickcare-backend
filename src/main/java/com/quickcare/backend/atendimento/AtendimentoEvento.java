package com.quickcare.backend.atendimento;

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
@Table(name = "atendimento_evento")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtendimentoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atendimento_id", nullable = false)
    private Atendimento atendimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoEventoAtendimento tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_usuario_id")
    private Usuario autor;

    @Column(nullable = false)
    private Instant inicio;

    @Column(nullable = false)
    private Instant termino;

    @Column(length = 500)
    private String detalhe;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

}
