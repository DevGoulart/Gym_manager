package com.academia.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login em duas etapas: depois da senha certa, o usuário recebe um "desafio" que vale 5 minutos e
 * aceita até 5 códigos errados. Só quando o código confere a sessão de verdade é aberta.
 * Guardamos apenas o hash do desafio, como fazemos com o token de sessão.
 */
@Component
public class DesafiosDoisFatores {

    public static final int MAX_TENTATIVAS = 5;
    private static final long VALIDADE_MINUTOS = 5;

    private record Desafio(Long usuarioId, Instant expiraEm, int tentativas) {
    }

    private final Map<String, Desafio> desafios = new ConcurrentHashMap<>();

    /** Cria o desafio e devolve o valor que vai para o navegador. */
    public String criar(Long usuarioId) {
        desafios.values().removeIf(d -> d.expiraEm().isBefore(Instant.now()));
        String valor = Tokens.novo();
        desafios.put(Tokens.hash(valor), new Desafio(usuarioId, Instant.now().plus(VALIDADE_MINUTOS, ChronoUnit.MINUTES), 0));
        return valor;
    }

    /** Usuário dono do desafio, ou null se ele não existe, expirou ou já gastou as tentativas. */
    public Long usuarioDe(String valor) {
        if (valor == null) return null;
        Desafio d = desafios.get(Tokens.hash(valor));
        if (d == null || d.expiraEm().isBefore(Instant.now()) || d.tentativas() >= MAX_TENTATIVAS) return null;
        return d.usuarioId();
    }

    /** Conta um código errado. Devolve quantas tentativas ainda restam. */
    public int registrarErro(String valor) {
        String chave = Tokens.hash(valor);
        Desafio d = desafios.computeIfPresent(chave, (k, atual) ->
                new Desafio(atual.usuarioId(), atual.expiraEm(), atual.tentativas() + 1));
        if (d == null || d.tentativas() >= MAX_TENTATIVAS) {
            desafios.remove(chave);
            return 0;
        }
        return MAX_TENTATIVAS - d.tentativas();
    }

    public void encerrar(String valor) {
        if (valor != null) desafios.remove(Tokens.hash(valor));
    }
}
