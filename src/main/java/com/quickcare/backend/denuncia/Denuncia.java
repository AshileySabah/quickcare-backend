package com.quickcare.backend.denuncia;

import com.quickcare.backend.atendimento.LadoAtendimento;
import com.quickcare.backend.ocorrencia.Ocorrencia;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.profissional.Profissional;
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

import java.time.Instant;

@Entity
@Table(name = "denuncia")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Denuncia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LadoAtendimento denunciante;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MotivoDenuncia motivo;

    @Column(nullable = false, length = 2000)
    private String descricao;

    @Column(name = "anexo_nome")
    private String anexoNome;

    @Column(name = "anexo_content_type", length = 100)
    private String anexoContentType;

    @Column(name = "anexo_tamanho_bytes")
    private Long anexoTamanhoBytes;

    @Column(name = "anexo_storage_key", length = 500)
    private String anexoStorageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusDenuncia status;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ConsequenciaDenuncia consequencia;

    @Column(name = "justificativa_decisao", length = 1000)
    private String justificativaDecisao;

    @Column(name = "analise_iniciada_em")
    private Instant analiseIniciadaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analisada_por_usuario_id")
    private Usuario analisadaPor;

    @Column(name = "decidida_em")
    private Instant decididaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decidida_por_usuario_id")
    private Usuario decididaPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ocorrencia_id")
    private Ocorrencia ocorrencia;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
        if (status == null) {
            status = StatusDenuncia.EM_ANALISE;
        }
    }

    public boolean temAnexo() {
        return anexoStorageKey != null;
    }

    public LadoAtendimento denunciado() {
        return denunciante == LadoAtendimento.PACIENTE ? LadoAtendimento.PROFISSIONAL : LadoAtendimento.PACIENTE;
    }

    public Usuario usuarioDenunciante() {
        return denunciante == LadoAtendimento.PACIENTE ? paciente.getUsuario() : profissional.getUsuario();
    }

    public Usuario usuarioDenunciado() {
        return denunciante == LadoAtendimento.PACIENTE ? profissional.getUsuario() : paciente.getUsuario();
    }

    public TipoPerfil perfilDenunciante() {
        return denunciante == LadoAtendimento.PACIENTE ? TipoPerfil.PACIENTE : TipoPerfil.PROFISSIONAL;
    }

    public TipoPerfil perfilDenunciado() {
        return denunciante == LadoAtendimento.PACIENTE ? TipoPerfil.PROFISSIONAL : TipoPerfil.PACIENTE;
    }

    public void julgar(StatusDenuncia resultado, ConsequenciaDenuncia consequencia, String justificativa,
                       Usuario admin, Instant agora) {
        exigir(status.pendente(), "Esta denúncia já foi julgada.");
        exigir(resultado != null && resultado.julgamento(), "Escolha se a denúncia é procedente ou improcedente.");
        if (resultado == StatusDenuncia.PROCEDENTE) {
            exigir(consequencia != null, "Escolha a consequência para o denunciado.");
        }
        status = resultado;
        this.consequencia = resultado == StatusDenuncia.PROCEDENTE ? consequencia : null;
        justificativaDecisao = justificativa.trim();
        decididaPor = admin;
        decididaEm = agora;
        if (analiseIniciadaEm == null) {
            analiseIniciadaEm = agora;
            analisadaPor = admin;
        }
    }

    public void vincularOcorrencia(Ocorrencia ocorrencia) {
        this.ocorrencia = ocorrencia;
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) {
            throw new TransicaoStatusInvalidaException(mensagem);
        }
    }

}
