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

    @Column(name = "anexo_storage_key")
    private String anexoStorageKey;

    @Column(name = "anexo_nome_original")
    private String anexoNomeOriginal;

    @Column(name = "anexo_tipo", length = 100)
    private String anexoTipo;

    public Mensagem(Long remetenteId, Long destinatarioId, String conteudo) {
        this.remetenteId = remetenteId;
        this.destinatarioId = destinatarioId;
        this.conteudo = conteudo;
        this.ativo = true;
    }

    public void anexar(String storageKey, String nomeOriginal, String tipo) {
        this.anexoStorageKey = storageKey;
        this.anexoNomeOriginal = nomeOriginal;
        this.anexoTipo = tipo;
    }

    public boolean possuiAnexo() {
        return anexoStorageKey != null;
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
