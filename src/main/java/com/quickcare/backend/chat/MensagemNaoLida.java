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

@Entity
@Table(name = "mensagem_nao_lida")
@Getter
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
public class MensagemNaoLida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mensagem_id", nullable = false)
    private Long mensagemId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "solicitacao_id", nullable = false)
    private Long solicitacaoId;

    @Column(name = "profissional_usuario_id")
    private Long profissionalUsuarioId;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant criadaEm;

    @Column(name = "email_enviado_em")
    private Instant emailEnviadoEm;

    public MensagemNaoLida(Mensagem mensagem, Long usuarioId, Instant criadaEm) {
        this.mensagemId = mensagem.getId();
        this.usuarioId = usuarioId;
        this.solicitacaoId = mensagem.getSolicitacaoId();
        this.profissionalUsuarioId = mensagem.getProfissionalUsuarioId();
        this.criadaEm = criadaEm;
    }

    public void marcarEmailEnviado(Instant quando) {
        this.emailEnviadoEm = quando;
    }

}
