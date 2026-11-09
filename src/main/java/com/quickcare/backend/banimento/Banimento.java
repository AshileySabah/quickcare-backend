package com.quickcare.backend.banimento;

import com.quickcare.backend.denuncia.Denuncia;
import com.quickcare.backend.solicitacao.exception.TransicaoStatusInvalidaException;
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
@Table(name = "banimento")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Banimento {

    public enum Encerramento {
        REMOVIDO,
        EXPIROU,
        RECURSO_ACEITO
    }

    public enum StatusRecurso {
        EM_ANALISE,
        ACEITO,
        NEGADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPerfil perfil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoBanimento tipo;

    @Column(nullable = false)
    private Instant inicio;

    private Instant fim;

    @Column(nullable = false, length = 1000)
    private String justificativa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aplicado_por_usuario_id")
    private Usuario aplicadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "denuncia_id")
    private Denuncia denuncia;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Encerramento encerramento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "encerrado_por_usuario_id")
    private Usuario encerradoPor;

    @Column(name = "justificativa_encerramento", length = 1000)
    private String justificativaEncerramento;

    @Column(name = "recurso_texto", length = 1000)
    private String recursoTexto;

    @Enumerated(EnumType.STRING)
    @Column(name = "recurso_status", length = 20)
    private StatusRecurso recursoStatus;

    @Column(name = "recurso_criado_em")
    private Instant recursoCriadoEm;

    @Column(name = "recurso_decidido_em")
    private Instant recursoDecididoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurso_decidido_por_usuario_id")
    private Usuario recursoDecididoPor;

    @Column(name = "recurso_justificativa", length = 1000)
    private String recursoJustificativa;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public boolean ativo(Instant agora) {
        return encerradoEm == null && (fim == null || agora.isBefore(fim));
    }

    public void prorrogarPorCongelamento(Instant congeladoEm, Duration duracao) {
        if (encerradoEm == null && fim != null && fim.isAfter(congeladoEm)) {
            fim = fim.plus(duracao);
        }
    }

    public boolean podeRecorrer(Instant agora) {
        return ativo(agora) && recursoStatus == null;
    }

    public void encerrar(Encerramento motivo, Usuario admin, String justificativa, Instant agora) {
        exigir(encerradoEm == null, "Este banimento já foi encerrado.");
        encerradoEm = agora;
        encerramento = motivo;
        encerradoPor = admin;
        justificativaEncerramento = justificativa == null ? null : justificativa.trim();
    }

    public void recorrer(String texto, Instant agora) {
        exigir(ativo(agora), "Este banimento já foi encerrado.");
        exigir(recursoStatus == null, "Você já recorreu deste banimento.");
        recursoTexto = texto.trim();
        recursoStatus = StatusRecurso.EM_ANALISE;
        recursoCriadoEm = agora;
    }

    public void decidirRecurso(boolean aceitar, Usuario admin, String justificativa, Instant agora) {
        exigir(recursoStatus == StatusRecurso.EM_ANALISE, "Este recurso já foi decidido.");
        recursoStatus = aceitar ? StatusRecurso.ACEITO : StatusRecurso.NEGADO;
        recursoDecididoPor = admin;
        recursoDecididoEm = agora;
        recursoJustificativa = justificativa.trim();
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new TransicaoStatusInvalidaException(mensagem);
        }
    }

}
