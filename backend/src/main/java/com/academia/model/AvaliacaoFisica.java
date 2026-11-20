package com.academia.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "avaliacoes_fisicas")
public class AvaliacaoFisica extends DaAcademia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "aluno_id", nullable = false)
    private Aluno aluno;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "professor_id")
    private Professor professor;

    @Column(nullable = false)
    private LocalDate dataAvaliacao;

    @Column(nullable = false)
    private Double peso;

    @Column(nullable = false)
    private Double altura;

    private Double percentualGordura;
    private Double cintura;
    private Double quadril;
    private Double braco;
    private Double coxa;

    @Column(length = 500)
    private String observacoes;

    @Transient
    public Double getImc() {
        if (peso == null || altura == null || altura <= 0) return null;
        return Math.round(peso / (altura * altura) * 100.0) / 100.0;
    }

    @Transient
    public String getClassificacaoImc() {
        Double imc = getImc();
        if (imc == null) return null;
        if (imc < 18.5) return "Abaixo do peso";
        if (imc < 25) return "Peso normal";
        if (imc < 30) return "Sobrepeso";
        if (imc < 35) return "Obesidade grau I";
        if (imc < 40) return "Obesidade grau II";
        return "Obesidade grau III";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Aluno getAluno() { return aluno; }
    public void setAluno(Aluno aluno) { this.aluno = aluno; }
    public Professor getProfessor() { return professor; }
    public void setProfessor(Professor professor) { this.professor = professor; }
    public LocalDate getDataAvaliacao() { return dataAvaliacao; }
    public void setDataAvaliacao(LocalDate dataAvaliacao) { this.dataAvaliacao = dataAvaliacao; }
    public Double getPeso() { return peso; }
    public void setPeso(Double peso) { this.peso = peso; }
    public Double getAltura() { return altura; }
    public void setAltura(Double altura) { this.altura = altura; }
    public Double getPercentualGordura() { return percentualGordura; }
    public void setPercentualGordura(Double percentualGordura) { this.percentualGordura = percentualGordura; }
    public Double getCintura() { return cintura; }
    public void setCintura(Double cintura) { this.cintura = cintura; }
    public Double getQuadril() { return quadril; }
    public void setQuadril(Double quadril) { this.quadril = quadril; }
    public Double getBraco() { return braco; }
    public void setBraco(Double braco) { this.braco = braco; }
    public Double getCoxa() { return coxa; }
    public void setCoxa(Double coxa) { this.coxa = coxa; }
    public String getObservacoes() { return observacoes; }
    public void setObservacoes(String observacoes) { this.observacoes = observacoes; }
}
