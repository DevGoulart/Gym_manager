package com.academia.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Geração de tokens de sessão aleatórios e do hash que fica no banco. */
public final class Tokens {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Tokens() {
    }

    /** 32 bytes aleatórios (256 bits) em Base64 seguro para URL. */
    public static String novo() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 em hexadecimal: é isso que fica gravado na tabela de sessões. */
    public static String hash(String token) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
