package com.quickcare.backend.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Mensagem de chat entre dois usuários. A exclusão é lógica ({@code ativo = false})
 * para preservar o histórico.
 */
@Entity
@Table(name = "mensagem")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "remetente_id", nullable = false)
    private Long remetenteId;

    @Column(name = "destinatario_id", nullable = false)
    private Long destinatarioId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String conteudo;

    @Column(name = "data_envio", nullable = false, updatable = false)
    private Instant dataEnvio;

    @Column(nullable = false)
    private boolean ativo;

    public Mensagem(Long remetenteId, Long destinatarioId, String conteudo) {
        this.remetenteId = remetenteId;
        this.destinatarioId = destinatarioId;
        this.conteudo = conteudo;
        this.ativo = true;
    }

    @PrePersist
    private void aoPersistir() {
        if (dataEnvio == null) {
            dataEnvio = Instant.now();
        }
    }

    public void excluir() {
        this.ativo = false;
    }

}
