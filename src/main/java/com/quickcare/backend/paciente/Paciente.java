package com.quickcare.backend.paciente;

import com.quickcare.backend.usuario.ContatoEmergencia;
import com.quickcare.backend.usuario.Genero;
import com.quickcare.backend.usuario.Usuario;
import com.quickcare.backend.validacao.StatusValidacao;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "paciente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Paciente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(nullable = false)
    private boolean banido;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_validacao", nullable = false, length = 20)
    @Builder.Default
    private StatusValidacao statusValidacao = StatusValidacao.PENDENTE;

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Genero genero;

    @ElementCollection
    @CollectionTable(name = "paciente_contato_emergencia", joinColumns = @JoinColumn(name = "paciente_id"))
    @Builder.Default
    private List<ContatoEmergencia> contatosEmergencia = new ArrayList<>();

    @Column(name = "confirmacao_informacoes_verdadeiras", nullable = false)
    private boolean confirmacaoInformacoesVerdadeiras;

    @Column(name = "consentimento_lgpd", nullable = false)
    private boolean consentimentoLgpd;

    @Column(length = 500)
    private String alergias;

    @Column(name = "condicoes_saude", length = 500)
    private String condicoesSaude;

    @Column(name = "medicamentos_em_uso", length = 500)
    private String medicamentosEmUso;

    @Column(nullable = false, length = 8)
    private String cep;

    @Column(nullable = false, length = 150)
    private String logradouro;

    @Column(nullable = false, length = 20)
    private String numero;

    @Column(length = 100)
    private String complemento;

    @Column(nullable = false, length = 100)
    private String bairro;

    @Column(nullable = false, length = 100)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;

    @Column(name = "excluido_em")
    private Instant excluidoEm;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public boolean encerrado() {
        return encerradoEm != null;
    }

    public boolean congelado() {
        return encerradoEm != null && excluidoEm == null;
    }

    public void anonimizar(Instant agora) {
        contatosEmergencia.clear();
        alergias = null;
        condicoesSaude = null;
        medicamentosEmUso = null;
        cep = "00000000";
        logradouro = "-";
        numero = "-";
        complemento = null;
        bairro = "-";
        excluidoEm = agora;
    }

}
