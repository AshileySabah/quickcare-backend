package com.quickcare.backend.atuacao;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
import com.quickcare.backend.profissional.Profissional;
import com.quickcare.backend.solicitacao.SolicitacaoProfissional;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "profissional_atuacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Atuacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaProfissional categoria;

    @ManyToMany
    @JoinTable(
            name = "profissional_atuacao_especialidade",
            joinColumns = @JoinColumn(name = "atuacao_id"),
            inverseJoinColumns = @JoinColumn(name = "especialidade_id")
    )
    @Builder.Default
    private Set<Especialidade> especialidades = new LinkedHashSet<>();

    @Column(name = "registro_profissional", length = 50)
    private String registroProfissional;

    @Column(name = "registro_profissional_uf", length = 2)
    private String registroProfissionalUf;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "removido_em")
    private Instant removidoEm;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

    public boolean ativa() {
        return removidoEm == null;
    }

    public boolean exigeRegistro() {
        return exigeRegistro(categoria);
    }

    public static boolean exigeRegistro(CategoriaProfissional categoria) {
        return categoria.getSiglaRegistro() != null;
    }

    public Set<Long> especialidadeIds() {
        return especialidades.stream().map(Especialidade::getId).collect(Collectors.toSet());
    }

    public List<Especialidade> especialidadesOrdenadas() {
        return especialidades.stream().sorted(Comparator.comparing(Especialidade::getNome)).toList();
    }

    public boolean atende(SolicitacaoProfissional linha) {
        return atende(linha, especialidadeIds());
    }

    public boolean atende(SolicitacaoProfissional linha, Set<Long> especialidadeIds) {
        return linha.getCategoria() == categoria
                && linha.getEspecialidades().stream().allMatch(especialidade -> especialidadeIds.contains(especialidade.getId()));
    }

    public String rotulo() {
        if (categoria == CategoriaProfissional.OUTRO && !especialidades.isEmpty()) {
            return especialidadesOrdenadas().getFirst().getNome();
        }
        return categoria.getRotulo();
    }

    public String rotuloDoRegistro() {
        return categoria.getSiglaRegistro() == null ? null : categoria.getSiglaRegistro() + " — " + rotulo();
    }

}
