package com.quickcare.backend.profissional;

import com.quickcare.backend.especialidade.Especialidade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Each specialty a professional practices is issued its own registration number by
 * that specialty's own professional council (CRN, CRP, CREFITO...), so the number
 * belongs to this link, not to the professional as a whole.
 */
@Entity
@Table(
        name = "profissional_especialidade",
        uniqueConstraints = @UniqueConstraint(columnNames = { "profissional_id", "especialidade_id" })
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfissionalEspecialidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "especialidade_id", nullable = false)
    private Especialidade especialidade;

    @Column(name = "registro_profissional", nullable = false, length = 50)
    private String registroProfissional;

}
