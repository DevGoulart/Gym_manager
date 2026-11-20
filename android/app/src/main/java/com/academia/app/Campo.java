package com.academia.app;

/** Descreve um campo de formulário de um módulo (o formulário é montado a partir desta descrição). */
public class Campo {

    public enum Tipo {
        TEXTO, MULTILINHA, EMAIL, TELEFONE, NUMERO, DECIMAL, DATA, BOOLEANO,
        /** Lista vinda da API (ex.: escolher o aluno). */
        SELECAO_API,
        /** Lista de opções fixas. */
        SELECAO_FIXA,
        /** Lista editável de exercícios (fichas de treino). */
        EXERCICIOS
    }

    public final String chave;
    public final String rotulo;
    public final Tipo tipo;
    public boolean obrigatorio;
    public String dica;
    /** Para SELECAO_API: endpoint que devolve a lista (cada item com "id" e "nome"). */
    public String endpoint;
    /** Para SELECAO_FIXA. */
    public String[] opcoes;
    /** Valor inicial ao criar um registro novo. "HOJE" para datas = data atual. */
    public Object padrao;

    public Campo(String chave, String rotulo, Tipo tipo) {
        this.chave = chave;
        this.rotulo = rotulo;
        this.tipo = tipo;
    }

    public Campo obrigatorio() {
        this.obrigatorio = true;
        return this;
    }

    public Campo dica(String dica) {
        this.dica = dica;
        return this;
    }

    public Campo endpoint(String endpoint) {
        this.endpoint = endpoint;
        return this;
    }

    public Campo opcoes(String... opcoes) {
        this.opcoes = opcoes;
        return this;
    }

    public Campo padrao(Object padrao) {
        this.padrao = padrao;
        return this;
    }
}
