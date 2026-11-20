package com.academia.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente HTTP que conversa com a API REST do back-end Spring Boot.
 * As chamadas rodam em segundo plano e o resultado volta na thread da interface.
 */
public final class Api {

    public interface Resposta {
        void sucesso(String corpo);

        void erro(String mensagem);
    }

    /** Emulador Android enxerga o computador pelo IP 10.0.2.2. */
    public static final String SERVIDOR_PADRAO = "http://10.0.2.2:8080";

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private Api() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    public static String servidor(Context c) {
        return prefs(c).getString("servidor", SERVIDOR_PADRAO);
    }

    public static void salvarServidor(Context c, String url) {
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://" + u;
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        prefs(c).edit().putString("servidor", u).apply();
    }

    public static void get(Context c, String caminho, Resposta r) {
        chamar(c, "GET", caminho, null, r);
    }

    public static void post(Context c, String caminho, String json, Resposta r) {
        chamar(c, "POST", caminho, json, r);
    }

    public static void put(Context c, String caminho, String json, Resposta r) {
        chamar(c, "PUT", caminho, json, r);
    }

    public static void delete(Context c, String caminho, Resposta r) {
        chamar(c, "DELETE", caminho, null, r);
    }

    private static void chamar(Context c, String metodo, String caminho, String json, Resposta r) {
        final String base = servidor(c);
        EXECUTOR.execute(() -> {
            HttpURLConnection con = null;
            try {
                con = (HttpURLConnection) new URL(base + caminho).openConnection();
                con.setRequestMethod(metodo);
                con.setConnectTimeout(6000);
                con.setReadTimeout(10000);
                con.setRequestProperty("Accept", "application/json");
                if (json != null) {
                    con.setDoOutput(true);
                    con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    try (OutputStream os = con.getOutputStream()) {
                        os.write(json.getBytes(StandardCharsets.UTF_8));
                    }
                }
                int status = con.getResponseCode();
                InputStream in = status >= 400 ? con.getErrorStream() : con.getInputStream();
                String corpo = ler(in);
                if (status >= 400) {
                    String msg = "Erro " + status;
                    try {
                        msg = new JSONObject(corpo).optString("erro", msg);
                    } catch (Exception ignored) {
                    }
                    final String m = msg;
                    UI.post(() -> r.erro(m));
                } else {
                    UI.post(() -> r.sucesso(corpo));
                }
            } catch (Exception e) {
                UI.post(() -> r.erro("Não foi possível conectar ao servidor " + base
                        + ".\nVerifique se o back-end está rodando e o endereço nas configurações (⚙)."));
            } finally {
                if (con != null) con.disconnect();
            }
        });
    }

    private static String ler(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream is = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
