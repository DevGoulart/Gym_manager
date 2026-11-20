package com.academia.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Rede de academias (plano Rede): várias unidades com um mesmo dono.
 * Cada unidade continua sendo uma {@link Academia} com seus próprios dados e administradores;
 * só o usuário com perfil {@link Perfil#DONO} enxerga e gerencia todas as unidades da rede.
 */
@Entity
@Table(name = "redes")
public class Rede {

    /** Quantas unidades o plano Rede permite. */
    public static final int LIMITE_PADRAO = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false)
    private int limiteUnidades = LIMITE_PADRAO;

    @Column(nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    public Rede() {
    }

    public Rede(String nome) {
        this.nome = nome;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public int getLimiteUnidades() { return limiteUnidades; }
    public void setLimiteUnidades(int limiteUnidades) { this.limiteUnidades = limiteUnidades; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
}
