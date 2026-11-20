package com.academia.app;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuração de um módulo do sistema (Alunos, Planos, ...): endpoint da API,
 * campos do formulário e como cada registro aparece na lista.
 */
public class Modulo {

    public interface Texto {
        String de(JSONObject o);
    }

    /** Selo colorido exibido à direita de cada item da lista. */
    public static class Selo {
        public final String texto;
        public final int cor;

        public Selo(String texto, int cor) {
            this.texto = texto;
            this.cor = cor;
        }
    }

    public interface SeloDe {
        Selo de(JSONObject o);
    }

    public final String id;
    public final String titulo;
    public final String singular;
    public final String icone;
    public final String endpoint;
    public final int cor;
    public final List<Campo> campos = new ArrayList<>();

    /** A busca é feita no servidor (?busca=) em vez de filtrar localmente. */
    public boolean buscaNoServidor;
    /** Filtros por situação (ex.: mensalidades): pares {rótulo, valor do parâmetro status}. */
    public String[][] filtrosStatus;

    public Texto tituloItem = o -> Formatos.txt(o, "nome");
    public Texto subtituloItem = o -> "";
    public SeloDe seloItem = o -> null;
    /** Resumo calculado pelo servidor exibido no topo da tela de edição. */
    public Texto resumo = o -> null;

    public Modulo(String id, String titulo, String singular, String icone, String endpoint, int cor) {
        this.id = id;
        this.titulo = titulo;
        this.singular = singular;
        this.icone = icone;
        this.endpoint = endpoint;
        this.cor = cor;
    }

    public Modulo campo(Campo c) {
        campos.add(c);
        return this;
    }
}
