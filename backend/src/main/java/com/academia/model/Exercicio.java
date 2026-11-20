package com.academia.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Exercicio {

    @Column(nullable = false, length = 100)
    private String nome;

    private Integer series;

    @Column(length = 20)
    private String repeticoes;

    @Column(length = 20)
    private String carga;

    public Exercicio() {
    }

    public Exercicio(String nome, Integer series, String repeticoes, String carga) {
        this.nome = nome;
        this.series = series;
        this.repeticoes = repeticoes;
        this.carga = carga;
    }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Integer getSeries() { return series; }
    public void setSeries(Integer series) { this.series = series; }
    public String getRepeticoes() { return repeticoes; }
    public void setRepeticoes(String repeticoes) { this.repeticoes = repeticoes; }
    public String getCarga() { return carga; }
    public void setCarga(String carga) { this.carga = carga; }
}
