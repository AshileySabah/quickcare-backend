package com.quickcare.backend.solicitacao;

import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.geolocalizacao.Coordenadas;
import com.quickcare.backend.paciente.Paciente;
import com.quickcare.backend.profissional.Profissional;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "solicitacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Solicitacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_id", nullable = false)
    private Paciente paciente;

    @OneToMany(mappedBy = "solicitacao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @Builder.Default
    private Set<SolicitacaoProfissional> profissionais = new LinkedHashSet<>();

    @Column(nullable = false, length = 1000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModalidadeSolicitacao modalidade;

    @Column(length = 8)
    private String cep;

    @Column(length = 150)
    private String logradouro;

    @Column(length = 20)
    private String numero;

    @Column(length = 100)
    private String complemento;

    @Column(length = 100)
    private String bairro;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String uf;

    /** Só no presencial; null se a geocodificação não encontrou o endereço. */
    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(name = "prazo_desejado", nullable = false)
    private LocalDate prazoDesejado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusSolicitacao status;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @PrePersist
    private void aoPersistir() {
        Instant agora = Instant.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        atualizadoEm = agora;
    }

    @PreUpdate
    private void aoAtualizar() {
        atualizadoEm = Instant.now();
    }

    /**
     * O profissional é compatível se atende a modalidade, alcança o endereço
     * (no presencial) e pode ocupar uma vaga ainda aberta. A consulta
     * {@link SolicitacaoRepository#findDisponiveisPara} faz o pré-filtro no
     * banco; a distância é conferida aqui.
     */
    public boolean atendidaPor(Profissional profissional) {
        return ModalidadeSolicitacao.atendidasPor(profissional.getModalidadeAtendimento()).contains(modalidade)
                && dentroDoRaioDe(profissional)
                && vagaDisponivelPara(profissional).isPresent();
    }

    /**
     * Linha com vaga aberta que o profissional pode ocupar. Entre várias, a mais
     * específica (mais especialidades exigidas), para não gastar com ele uma
     * vaga que um profissional menos especializado também preencheria.
     */
    public Optional<SolicitacaoProfissional> vagaDisponivelPara(Profissional profissional) {
        Set<Long> especialidadeIds = profissional.getEspecialidades().stream()
                .map(Especialidade::getId)
                .collect(Collectors.toSet());

        return profissionais.stream()
                .filter(SolicitacaoProfissional::temVaga)
                .filter(pedido -> pedido.atendidaPor(profissional.getCategoria(), especialidadeIds))
                .max(Comparator.comparingInt(pedido -> pedido.getEspecialidades().size()));
    }

    public boolean todasAsVagasPreenchidas() {
        return profissionais.stream().noneMatch(SolicitacaoProfissional::temVaga);
    }

    public boolean temVagaPreenchida() {
        return profissionais.stream().anyMatch(pedido -> pedido.getVagasPreenchidas() > 0);
    }

    /**
     * Online não tem distância. No presencial, compara as coordenadas com o raio
     * do profissional; se um dos lados não pôde ser geocodificado (ou não há
     * raio), aceita quando é a mesma cidade.
     */
    private boolean dentroDoRaioDe(Profissional profissional) {
        if (modalidade != ModalidadeSolicitacao.PRESENCIAL) {
            return true;
        }

        Coordenadas daSolicitacao = Coordenadas.de(latitude, longitude);
        Coordenadas doProfissional = Coordenadas.de(profissional.getLatitude(), profissional.getLongitude());
        BigDecimal raioKm = profissional.getRaioAtendimentoKm();

        if (daSolicitacao != null && doProfissional != null && raioKm != null) {
            return doProfissional.distanciaKm(daSolicitacao) <= raioKm.doubleValue();
        }

        return mesmoTexto(uf, profissional.getUf()) && mesmoTexto(cidade, profissional.getCidade());
    }

    /** Ignora maiúsculas, acentos e espaços nas pontas ("São Paulo" = "sao paulo"). */
    private static boolean mesmoTexto(String a, String b) {
        return a != null && b != null && normalizar(a).equals(normalizar(b));
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    public boolean ehDoUsuario(Long usuarioId) {
        return paciente.getUsuario().getId().equals(usuarioId);
    }

}
