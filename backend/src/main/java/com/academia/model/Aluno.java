package com.academia.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "alunos", uniqueConstraints = @UniqueConstraint(name = "uk_aluno_academia_cpf", columnNames = {"academia_id", "cpf"}))
public class Aluno extends DaAcademia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 14)
    private String cpf;

    @Column(length = 120)
    private String email;

    @Column(length = 20)
    private String telefone;

    private LocalDate dataNascimento;

    @Column(nullable = false)
    private LocalDate dataMatricula;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plano_id")
    private Plano plano;

    @Column(nullable = false)
    private boolean ativo = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getCpf() { return cpf; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public LocalDate getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(LocalDate dataNascimento) { this.dataNascimento = dataNascimento; }
    public LocalDate getDataMatricula() { return dataMatricula; }
    public void setDataMatricula(LocalDate dataMatricula) { this.dataMatricula = dataMatricula; }
    public Plano getPlano() { return plano; }
    public void setPlano(Plano plano) { this.plano = plano; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}
