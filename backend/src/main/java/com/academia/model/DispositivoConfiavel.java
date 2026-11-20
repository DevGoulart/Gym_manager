package com.academia.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Navegador em que a pessoa marcou "Lembrar deste dispositivo por 30 dias" na verificação em duas etapas.
 * Nele, o login continua pedindo a senha, mas dispensa o código do aplicativo até a data de validade.
 * Como nas sessões, o banco guarda só o hash SHA-256 do token; o token fica no navegador.
 */
@Entity
@Table(name = "dispositivos_confiaveis", indexes = @Index(name = "idx_dispositivo_usuario", columnList = "usuario_id"))
public class DispositivoConfiavel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Ex.: "Edge · Windows" (tirado do navegador, só para a pessoa reconhecer na lista). */
    @Column(nullable = false, length = 120)
    private String descricao;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    @Column(nullable = false)
    private LocalDateTime expiraEm;

    private LocalDateTime ultimoUsoEm;

    public DispositivoConfiavel() {
    }

    public DispositivoConfiavel(String tokenHash, Usuario usuario, String descricao, LocalDateTime criadoEm, LocalDateTime expiraEm) {
        this.tokenHash = tokenHash;
        this.usuario = usuario;
        this.descricao = descricao;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    public Long getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public Usuario getUsuario() { return usuario; }
    public String getDescricao() { return descricao; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getExpiraEm() { return expiraEm; }
    public LocalDateTime getUltimoUsoEm() { return ultimoUsoEm; }
    public void setUltimoUsoEm(LocalDateTime ultimoUsoEm) { this.ultimoUsoEm = ultimoUsoEm; }
}
