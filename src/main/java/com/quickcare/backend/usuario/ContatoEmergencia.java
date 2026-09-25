package com.quickcare.backend.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContatoEmergencia {

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "telefone", nullable = false, length = 20)
    private String telefone;

}
