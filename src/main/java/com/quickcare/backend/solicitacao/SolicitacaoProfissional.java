package com.quickcare.backend.solicitacao;

import com.quickcare.backend.especialidade.CategoriaProfissional;
import com.quickcare.backend.especialidade.Especialidade;
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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Quantos profissionais de uma categoria a solicitação pede e quais
 * especialidades cada um deles precisa atender — todas, não qualquer uma.
 */
@Entity
@Table(name = "solicitacao_profissional")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitacaoProfissional {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitacao_id", nullable = false)
    private Solicitacao solicitacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaProfissional categoria;

    @ManyToMany
    @JoinTable(
            name = "solicitacao_profissional_especialidade",
            joinColumns = @JoinColumn(name = "solicitacao_profissional_id"),
            inverseJoinColumns = @JoinColumn(name = "especialidade_id")
    )
    @Builder.Default
    private Set<Especialidade> especialidades = new LinkedHashSet<>();

    @Column(nullable = false)
    private int quantidade;

    /** Propostas aceitas para esta linha; nunca passa de {@code quantidade}. */
    @Column(name = "vagas_preenchidas", nullable = false)
    private int vagasPreenchidas;

    /** O profissional atende esta linha se for da categoria e cobrir todas as especialidades. */
    public boolean atendidaPor(CategoriaProfissional categoriaProfissional, Set<Long> especialidadeIdsDoProfissional) {
        return categoria == categoriaProfissional
                && especialidades.stream().allMatch(especialidade -> especialidadeIdsDoProfissional.contains(especialidade.getId()));
    }

    public boolean temVaga() {
        return vagasPreenchidas < quantidade;
    }

    public void preencherVaga() {
        if (!temVaga()) {
            throw new IllegalStateException("Linha sem vagas.");
        }
        vagasPreenchidas++;
    }

}
