package com.quickcare.backend.profissional;

import com.quickcare.backend.atuacao.Atuacao;
import com.quickcare.backend.geolocalizacao.Coordenadas;
import com.quickcare.backend.geolocalizacao.EnderecoGeocodificavel;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "profissional")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Profissional {

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

    @OneToMany(mappedBy = "profissional")
    @OrderBy("criadoEm ASC, id ASC")
    @Builder.Default
    private List<Atuacao> atuacoes = new ArrayList<>();

    @Column(name = "data_nascimento", nullable = false)
    private LocalDate dataNascimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Genero genero;

    @ElementCollection
    @CollectionTable(name = "profissional_contato_emergencia", joinColumns = @JoinColumn(name = "profissional_id"))
    @Builder.Default
    private List<ContatoEmergencia> contatosEmergencia = new ArrayList<>();

    @Column(name = "confirmacao_informacoes_verdadeiras", nullable = false)
    private boolean confirmacaoInformacoesVerdadeiras;

    @Column(name = "consentimento_lgpd", nullable = false)
    private boolean consentimentoLgpd;

    @Column(name = "possui_seguro_responsabilidade_civil", nullable = false)
    private boolean possuiSeguroResponsabilidadeCivil;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade_atendimento", nullable = false)
    private ModalidadeAtendimento modalidadeAtendimento;

    @Column(name = "raio_atendimento_km")
    private BigDecimal raioAtendimentoKm;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(length = 14)
    private String cnpj;

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

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public List<Atuacao> atuacoesAtivas() {
        return atuacoes.stream().filter(Atuacao::ativa).toList();
    }

    public EnderecoGeocodificavel endereco() {
        return new EnderecoGeocodificavel(cep, logradouro, numero, cidade, uf);
    }

    public void definirCoordenadas(Coordenadas coordenadas) {
        latitude = coordenadas == null ? null : coordenadas.latitude();
        longitude = coordenadas == null ? null : coordenadas.longitude();
    }

}
