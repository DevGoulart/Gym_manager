package com.academia.model;

import jakarta.persistence.*;

@Entity
@Table(name = "professores", uniqueConstraints = @UniqueConstraint(name = "uk_professor_academia_cpf", columnNames = {"academia_id", "cpf"}))
public class Professor extends DaAcademia {

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

    @Column(length = 80)
    private String especialidade;

    @Column(length = 20)
    private String cref;

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
    public String getEspecialidade() { return especialidade; }
    public void setEspecialidade(String especialidade) { this.especialidade = especialidade; }
    public String getCref() { return cref; }
    public void setCref(String cref) { this.cref = cref; }
}
