package com.academia.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Códigos de verificação em duas etapas (TOTP, RFC 6238) — o mesmo padrão do Google Authenticator,
 * Microsoft Authenticator e Authy.
 *
 * O servidor e o aplicativo do celular guardam o mesmo segredo. A cada 30 segundos os dois calculam
 * HMAC-SHA1(segredo, número do intervalo de 30 s) e tiram dele um número de 6 dígitos. Como o cálculo
 * depende só do segredo e do relógio, o celular não precisa de internet.
 */
public final class Totp {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int PERIODO_SEGUNDOS = 30;
    /** Aceita o código do intervalo anterior e do seguinte, para tolerar relógios um pouco fora de hora. */
    private static final int TOLERANCIA = 1;

    private Totp() {
    }

    /** Novo segredo de 160 bits em Base32 (o formato que os aplicativos autenticadores leem). */
    public static String novoSegredo() {
        byte[] bytes = new byte[20];
        ALEATORIO.nextBytes(bytes);
        return base32(bytes);
    }

    /** Intervalo de 30 segundos atual, contado desde 1970. */
    public static long passoAtual() {
        return Instant.now().getEpochSecond() / PERIODO_SEGUNDOS;
    }

    /** O código de 6 dígitos de um intervalo. */
    public static String codigo(String segredoBase32, long passo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodificarBase32(segredoBase32), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(passo).array());
            int deslocamento = h[h.length - 1] & 0x0f;
            int binario = ((h[deslocamento] & 0x7f) << 24) | ((h[deslocamento + 1] & 0xff) << 16)
                    | ((h[deslocamento + 2] & 0xff) << 8) | (h[deslocamento + 3] & 0xff);
            return String.format("%06d", binario % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Confere um código digitado. Devolve o intervalo em que ele vale (para impedir que o mesmo código
     * seja usado duas vezes) ou -1 se for inválido. Códigos de intervalos já usados são recusados.
     */
    public static long verificar(String segredoBase32, String codigoDigitado, Long ultimoPassoUsado) {
        String codigo = codigoDigitado == null ? "" : codigoDigitado.replaceAll("\\s", "");
        if (segredoBase32 == null || !codigo.matches("\\d{6}")) return -1;
        long atual = passoAtual();
        for (long passo = atual - TOLERANCIA; passo <= atual + TOLERANCIA; passo++) {
            if (ultimoPassoUsado != null && passo <= ultimoPassoUsado) continue;
            boolean igual = MessageDigest.isEqual(
                    codigo(segredoBase32, passo).getBytes(StandardCharsets.US_ASCII),
                    codigo.getBytes(StandardCharsets.US_ASCII));
            if (igual) return passo;
        }
        return -1;
    }

    /** Endereço otpauth:// que vira o QR Code lido pelo aplicativo. */
    public static String uri(String emissor, String conta, String segredoBase32) {
        String rotulo = codificar(emissor + ":" + conta);
        return "otpauth://totp/" + rotulo + "?secret=" + segredoBase32 + "&issuer=" + codificar(emissor)
                + "&algorithm=SHA1&digits=6&period=" + PERIODO_SEGUNDOS;
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String base32(byte[] dados) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : dados) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] decodificarBase32(String texto) {
        String limpo = texto.replaceAll("[\\s=-]", "").toUpperCase();
        ByteBuffer saida = ByteBuffer.allocate(limpo.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char c : limpo.toCharArray()) {
            int valor = BASE32.indexOf(c);
            if (valor < 0) throw new IllegalArgumentException("Segredo Base32 inválido");
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                saida.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return saida.array();
    }
}
