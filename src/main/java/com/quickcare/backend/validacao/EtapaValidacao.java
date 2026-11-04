package com.quickcare.backend.validacao;

import com.quickcare.backend.documento.Documento;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.profissional.Profissional;
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
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "etapa_validacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtapaValidacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id")
    private Profissional profissional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoEtapaValidacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEtapa status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id")
    private Documento documento;

    @Column(length = 1000)
    private String justificativa;

    @Column(name = "anexo_storage_key", length = 500)
    private String anexoStorageKey;

    @Column(name = "anexo_nome_arquivo")
    private String anexoNomeArquivo;

    @Column(name = "anexo_content_type", length = 100)
    private String anexoContentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avaliado_por_id")
    private Usuario avaliadoPor;

    @Column(name = "avaliado_em")
    private Instant avaliadoEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public static EtapaValidacao pendente(TipoEtapaValidacao tipo, Documento documento, Paciente paciente, Profissional profissional) {
        return EtapaValidacao.builder()
                .tipo(tipo)
                .status(StatusEtapa.PENDENTE)
                .documento(documento)
                .paciente(paciente)
                .profissional(profissional)
                .build();
    }

}
