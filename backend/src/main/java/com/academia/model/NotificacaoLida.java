package com.academia.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Aviso calculado que a pessoa já leu (ex.: "financeiro:atrasadas:2026-11-21").
 * A chave leva a data ou o número quando o aviso deve voltar a aparecer depois.
 */
@Entity
@Table(name = "notificacoes_lidas", uniqueConstraints = @UniqueConstraint(name = "uk_notificacao_lida", columnNames = {"usuarioId", "chave"}))
public class NotificacaoLida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 150)
    private String chave;

    @Column(nullable = false)
    private LocalDateTime lidaEm = LocalDateTime.now();

    public NotificacaoLida() {
    }

    public NotificacaoLida(Long usuarioId, String chave) {
        this.usuarioId = usuarioId;
        this.chave = chave;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getChave() { return chave; }
    public LocalDateTime getLidaEm() { return lidaEm; }
}
