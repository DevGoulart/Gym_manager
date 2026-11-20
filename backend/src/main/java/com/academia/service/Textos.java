package com.academia.service;

final class Textos {

    private Textos() {
    }

    static String limpar(String valor) {
        if (valor == null) return null;
        String t = valor.trim();
        return t.isEmpty() ? null : t;
    }
}
