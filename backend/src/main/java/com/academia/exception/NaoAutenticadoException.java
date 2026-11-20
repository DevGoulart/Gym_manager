package com.academia.exception;

/** Login inválido ou sessão ausente: vira HTTP 401. */
public class NaoAutenticadoException extends RuntimeException {
    public NaoAutenticadoException(String mensagem) {
        super(mensagem);
    }
}
