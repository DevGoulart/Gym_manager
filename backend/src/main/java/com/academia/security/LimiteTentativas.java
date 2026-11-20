package com.academia.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proteção contra adivinhação de senha: depois de 5 tentativas erradas em 15 minutos
 * para o mesmo e-mail, o login desse e-mail fica bloqueado até a janela passar.
 */
@Component
public class LimiteTentativas {

    private static final int MAXIMO = 5;
    private static final Duration JANELA = Duration.ofMinutes(15);

    private final Map<String, Deque<Instant>> falhas = new ConcurrentHashMap<>();

    public boolean bloqueado(String email) {
        Deque<Instant> lista = falhas.get(email);
        if (lista == null) return false;
        synchronized (lista) {
            limpar(lista);
            return lista.size() >= MAXIMO;
        }
    }

    public void registrarFalha(String email) {
        Deque<Instant> lista = falhas.computeIfAbsent(email, k -> new ArrayDeque<>());
        synchronized (lista) {
            limpar(lista);
            lista.addLast(Instant.now());
        }
    }

    public void limparFalhas(String email) {
        falhas.remove(email);
    }

    private static void limpar(Deque<Instant> lista) {
        Instant limite = Instant.now().minus(JANELA);
        while (!lista.isEmpty() && lista.peekFirst().isBefore(limite)) lista.removeFirst();
    }
}
