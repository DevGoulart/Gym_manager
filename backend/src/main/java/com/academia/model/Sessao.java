package com.academia.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Sessão de login. O navegador guarda o token; o banco guarda só o hash SHA-256 dele,
 * então quem ler o banco não consegue usar as sessões.
 */
@Entity
@Table(name = "sessoes")
public class Sessao {

    @Id
    @Column(length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private LocalDateTime criadaEm;

    @Column(nullable = false)
    private LocalDateTime expiraEm;

    public Sessao() {
    }

    public Sessao(String tokenHash, Usuario usuario, LocalDateTime criadaEm, LocalDateTime expiraEm) {
        this.tokenHash = tokenHash;
        this.usuario = usuario;
        this.criadaEm = criadaEm;
        this.expiraEm = expiraEm;
    }

    public String getTokenHash() { return tokenHash; }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
}
