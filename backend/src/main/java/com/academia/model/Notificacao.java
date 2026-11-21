package com.academia.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Notificação gravada (um evento que aconteceu): pagamento aprovado, senha alterada,
 * verificação em duas etapas ligada ou desligada... Os avisos que dependem do estado atual
 * (mensalidades atrasadas, aniversariantes, assinatura vencendo) não ficam aqui: são
 * calculados na hora pelo NotificacaoService.
 * Sem chave estrangeira para o usuário: quem apaga a conta apaga as notificações dela.
 */
@Entity
@Table(name = "notificacoes", indexes = @Index(name = "ix_notificacao_usuario", columnList = "usuarioId, criadaEm"))
public class Notificacao {

    public enum Tipo { FINANCEIRO, ALUNOS, ASSINATURA, REDE, SEGURANCA }
    public enum Nivel { INFO, ATENCAO, URGENTE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 15)
    private Tipo tipo;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 10)
    private Nivel nivel;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(length = 300)
    private String texto;

    @Column(length = 120)
    private String link;

    @Column(nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    private LocalDateTime lidaEm;

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public Nivel getNivel() { return nivel; }
    public void setNivel(Nivel nivel) { this.nivel = nivel; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
    public LocalDateTime getLidaEm() { return lidaEm; }
    public void setLidaEm(LocalDateTime lidaEm) { this.lidaEm = lidaEm; }
}
