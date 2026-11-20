package com.academia.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Uma academia cliente do sistema. Todos os dados (alunos, planos, mensalidades...) pertencem a uma academia. */
@Entity
@Table(name = "academias")
public class Academia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 80)
    private String cidade;

    @Column(nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    @Column(nullable = false)
    private boolean ativa = true;

    public Academia() {
    }

    public Academia(String nome, String cidade) {
        this.nome = nome;
        this.cidade = cidade;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public void setCriadaEm(LocalDateTime criadaEm) { this.criadaEm = criadaEm; }
    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }
}
