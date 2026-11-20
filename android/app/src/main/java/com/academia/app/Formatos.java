package com.academia.app;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

/** Conversões de datas e números entre o formato da API e o formato brasileiro exibido na tela. */
public final class Formatos {

    private static final Locale BR = new Locale("pt", "BR");

    private Formatos() {
    }

    /** "2026-11-18" → "18/11/2026" */
    public static String data(String iso) {
        if (iso == null || iso.length() < 10 || iso.equals("null")) return "";
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
    }

    /** "18/11/2026" → "2026-11-18" */
    public static String isoDe(String br) {
        if (br == null || br.length() != 10) return null;
        return br.substring(6, 10) + "-" + br.substring(3, 5) + "-" + br.substring(0, 2);
    }

    public static String hojeIso() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(java.util.Calendar.YEAR), c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    public static String dinheiro(double valor) {
        return NumberFormat.getCurrencyInstance(BR).format(valor);
    }

    public static String numero(double valor) {
        NumberFormat nf = NumberFormat.getNumberInstance(BR);
        nf.setMaximumFractionDigits(2);
        return nf.format(valor);
    }

    /** Número com sinal: +1,5 / -3,2 */
    public static String variacao(double valor) {
        return (valor > 0 ? "+" : "") + numero(valor);
    }

    /** Lê um campo de texto do JSON, devolvendo "" quando nulo. */
    public static String txt(JSONObject o, String chave) {
        if (o == null || o.isNull(chave)) return "";
        return o.optString(chave, "");
    }

    public static boolean tem(JSONObject o, String chave) {
        return o != null && o.has(chave) && !o.isNull(chave);
    }

    /** Aceita "65,5" ou "65.5". */
    public static Double lerDecimal(String s) {
        if (s == null) return null;
        String t = s.trim().replace(" ", "");
        if (t.isEmpty()) return null;
        if (t.contains(",")) t = t.replace(".", "").replace(",", ".");
        return Double.parseDouble(t);
    }
}
