package com.academia.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Assinatura do sistema. Uma por conta pagante: a academia avulsa ou a rede inteira
 * (as unidades de uma rede não têm assinatura própria).
 *
 * Situação, calculada pelas datas:
 *  - TESTE:   ainda não pagou e está dentro dos 14 dias de teste (tudo liberado);
 *  - ATIVA:   pagou e o período pago não terminou;
 *  - VENCIDA: o teste ou o período pago terminou (bloqueia novos alunos, usuários e unidades).
 */
@Entity
@Table(name = "assinaturas")
public class Assinatura {

    public enum Situacao { TESTE, ATIVA, VENCIDA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academia_id", unique = true)
    private Academia academia;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rede_id", unique = true)
    private Rede rede;

    /** Plano pago (vazio enquanto só está no teste). VARCHAR, para novos planos não exigirem migração. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private PlanoSistema plano;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 10)
    private CicloCobranca ciclo;

    @Column(nullable = false)
    private LocalDate testeAte;

    /** Período pago atual: de inicioPeriodo até vigenteAte (inclusive), que custou valorPeriodo. */
    private LocalDate inicioPeriodo;
    private LocalDate vigenteAte;

    @Column(precision = 10, scale = 2)
    private BigDecimal valorPeriodo;

    /** Vagas de unidade compradas além das 5 do plano Rede. */
    @Column(nullable = false)
    private int unidadesExtras;

    @Column(nullable = false)
    private LocalDateTime criadaEm = LocalDateTime.now();

    public Situacao situacao(LocalDate hoje) {
        if (vigenteAte != null && !hoje.isAfter(vigenteAte)) return Situacao.ATIVA;
        if (vigenteAte == null && !hoje.isAfter(testeAte)) return Situacao.TESTE;
        return Situacao.VENCIDA;
    }

    public Long getId() { return id; }
    public Academia getAcademia() { return academia; }
    public void setAcademia(Academia academia) { this.academia = academia; }
    public Rede getRede() { return rede; }
    public void setRede(Rede rede) { this.rede = rede; }
    public PlanoSistema getPlano() { return plano; }
    public void setPlano(PlanoSistema plano) { this.plano = plano; }
    public CicloCobranca getCiclo() { return ciclo; }
    public void setCiclo(CicloCobranca ciclo) { this.ciclo = ciclo; }
    public LocalDate getTesteAte() { return testeAte; }
    public void setTesteAte(LocalDate testeAte) { this.testeAte = testeAte; }
    public LocalDate getInicioPeriodo() { return inicioPeriodo; }
    public void setInicioPeriodo(LocalDate inicioPeriodo) { this.inicioPeriodo = inicioPeriodo; }
    public LocalDate getVigenteAte() { return vigenteAte; }
    public void setVigenteAte(LocalDate vigenteAte) { this.vigenteAte = vigenteAte; }
    public BigDecimal getValorPeriodo() { return valorPeriodo; }
    public void setValorPeriodo(BigDecimal valorPeriodo) { this.valorPeriodo = valorPeriodo; }
    public int getUnidadesExtras() { return unidadesExtras; }
    public void setUnidadesExtras(int unidadesExtras) { this.unidadesExtras = unidadesExtras; }
    public LocalDateTime getCriadaEm() { return criadaEm; }
}
