package com.academia.exception;

/** Muitas tentativas de login erradas seguidas: vira HTTP 429. */
public class MuitasTentativasException extends RuntimeException {
    public MuitasTentativasException(String mensagem) {
        super(mensagem);
    }
}
