package com.quickcare.backend.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Participação de um profissional na conversa de uma solicitação. Enquanto {@code saiuEm} é nulo
 * ele conversa normalmente; depois disso só enxerga o que foi escrito até {@code saiuEm}.
 */
@Entity
@Table(name = "conversa_profissional")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class ConversaProfissional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitacao_id", nullable = false)
    private Long solicitacaoId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "entrou_em", nullable = false)
    private Instant entrouEm;

    @Column(name = "saiu_em")
    private Instant saiuEm;

    public ConversaProfissional(Long solicitacaoId, Long usuarioId, Instant entrouEm) {
        this.solicitacaoId = solicitacaoId;
        this.usuarioId = usuarioId;
        this.entrouEm = entrouEm;
    }

    public boolean ativo() {
        return saiuEm == null;
    }

    /** Nunca marca a saída antes da entrada. */
    public void sair(Instant quando) {
        this.saiuEm = quando.isBefore(entrouEm) ? entrouEm : quando;
    }

    /** O profissional voltou a ter proposta aceita: a conversa reabre para ele. */
    public void reabrir() {
        this.saiuEm = null;
    }

}
