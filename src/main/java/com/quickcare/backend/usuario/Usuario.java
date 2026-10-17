package com.quickcare.backend.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(nullable = false, unique = true)
    private String cpf;

    private String telefone;

    @Enumerated(EnumType.STRING)
    @Column(name = "documento_identidade_tipo", length = 10)
    private TipoDocumentoIdentidade documentoIdentidadeTipo;

    @Column(name = "documento_identidade_numero", length = 20)
    private String documentoIdentidadeNumero;

    @Column(name = "documento_identidade_uf", length = 2)
    private String documentoIdentidadeUf;

    @Column(name = "avatar_storage_key")
    private String avatarStorageKey;

    @Column(name = "senha_alterada_em")
    private Instant senhaAlteradaEm;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @PrePersist
    private void aoPersistir() {
        if (criadoEm == null) {
            criadoEm = Instant.now();
        }
    }

}
