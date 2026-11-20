package com.academia.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Pessoa que entra no sistema. O e-mail é o login e é único em todo o sistema. */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "academia_id", nullable = false)
    private Academia academia;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    /** Hash BCrypt da senha. A senha em texto nunca é gravada. */
    @Column(nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    // ---------- Verificação em duas etapas (TOTP) ----------
    // Tipos que aceitam null: as colunas são novas e a tabela já tinha usuários.

    /** Segredo TOTP em Base32, compartilhado com o aplicativo autenticador do celular. */
    @Column(length = 64)
    private String totpSegredo;

    /** Segredo gerado ao começar a ativação; só vira o segredo oficial quando o primeiro código confere. */
    @Column(length = 64)
    private String totpPendente;

    private Boolean totpAtivo;

    /** Último intervalo de 30 s já usado: impede reutilizar o mesmo código. */
    private Long totpUltimoPasso;

    private LocalDateTime totpAtivadoEm;

    /** Hashes SHA-256 dos códigos de recuperação ainda não usados, separados por vírgula. */
    @Column(length = 1000)
    private String codigosRecuperacao;

    public boolean doisFatoresAtivo() { return Boolean.TRUE.equals(totpAtivo); }
    public String getTotpSegredo() { return totpSegredo; }
    public void setTotpSegredo(String totpSegredo) { this.totpSegredo = totpSegredo; }
    public String getTotpPendente() { return totpPendente; }
    public void setTotpPendente(String totpPendente) { this.totpPendente = totpPendente; }
    public Boolean getTotpAtivo() { return totpAtivo; }
    public void setTotpAtivo(Boolean totpAtivo) { this.totpAtivo = totpAtivo; }
    public Long getTotpUltimoPasso() { return totpUltimoPasso; }
    public void setTotpUltimoPasso(Long totpUltimoPasso) { this.totpUltimoPasso = totpUltimoPasso; }
    public LocalDateTime getTotpAtivadoEm() { return totpAtivadoEm; }
    public void setTotpAtivadoEm(LocalDateTime totpAtivadoEm) { this.totpAtivadoEm = totpAtivadoEm; }
    public String getCodigosRecuperacao() { return codigosRecuperacao; }
    public void setCodigosRecuperacao(String codigosRecuperacao) { this.codigosRecuperacao = codigosRecuperacao; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Academia getAcademia() { return academia; }
    public void setAcademia(Academia academia) { this.academia = academia; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getSenhaHash() { return senhaHash; }
    public void setSenhaHash(String senhaHash) { this.senhaHash = senhaHash; }
    public Perfil getPerfil() { return perfil; }
    public void setPerfil(Perfil perfil) { this.perfil = perfil; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
