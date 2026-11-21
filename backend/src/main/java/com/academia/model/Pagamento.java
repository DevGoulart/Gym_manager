package com.academia.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pagamento de uma assinatura (gateway de demonstração: nenhum valor é cobrado de verdade).
 * Guarda o pedido (plano, ciclo, vagas) para aplicar quando for aprovado: cartão na hora,
 * Pix e boleto quando a compensação é simulada. Do cartão ficam só a bandeira e os 4 últimos dígitos.
 */
@Entity
@Table(name = "pagamentos")
public class Pagamento {

    public enum Tipo { PLANO, UNIDADES }
    public enum Metodo { CARTAO, PIX, BOLETO, CREDITO }
    public enum Status { PENDENTE, APROVADO, CANCELADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assinatura_id", nullable = false)
    private Assinatura assinatura;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 10)
    private Tipo tipo;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(length = 20)
    private PlanoSistema plano;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(length = 10)
    private CicloCobranca ciclo;

    /** PLANO: total de vagas extras depois do pagamento. UNIDADES: quantas vagas estão sendo compradas. */
    @Column(nullable = false)
    private int unidadesExtras;

    /** Academia avulsa assinando o plano Rede: nome da rede a criar quando aprovar. */
    @Column(length = 120)
    private String nomeRede;

    /** Mesmo plano e ciclo, ainda ativo: o novo período começa quando o atual termina. */
    private boolean renovacao;
    private LocalDate periodoInicio;
    private LocalDate periodoFim;

    @Column(nullable = false, length = 200)
    private String descricao;

    /** Preço cheio do pedido, o crédito do período não usado e o valor pago. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal credito = BigDecimal.ZERO;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 10)
    private Metodo metodo;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 10)
    private Status status;

    @Column(length = 20)
    private String cartaoBandeira;
    @Column(length = 4)
    private String cartaoFinal;

    /** Pix copia e cola ou linha digitável do boleto. */
    @Column(length = 400)
    private String codigo;
    private LocalDateTime venceEm;

    /** Quem fez o pedido (só o nome: o usuário pode ser excluído depois). */
    @Column(length = 120)
    private String responsavel;

    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime pagoEm;

    public Long getId() { return id; }
    public Assinatura getAssinatura() { return assinatura; }
    public void setAssinatura(Assinatura assinatura) { this.assinatura = assinatura; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public PlanoSistema getPlano() { return plano; }
    public void setPlano(PlanoSistema plano) { this.plano = plano; }
    public CicloCobranca getCiclo() { return ciclo; }
    public void setCiclo(CicloCobranca ciclo) { this.ciclo = ciclo; }
    public int getUnidadesExtras() { return unidadesExtras; }
    public void setUnidadesExtras(int unidadesExtras) { this.unidadesExtras = unidadesExtras; }
    public String getNomeRede() { return nomeRede; }
    public void setNomeRede(String nomeRede) { this.nomeRede = nomeRede; }
    public boolean isRenovacao() { return renovacao; }
    public void setRenovacao(boolean renovacao) { this.renovacao = renovacao; }
    public LocalDate getPeriodoInicio() { return periodoInicio; }
    public void setPeriodoInicio(LocalDate periodoInicio) { this.periodoInicio = periodoInicio; }
    public LocalDate getPeriodoFim() { return periodoFim; }
    public void setPeriodoFim(LocalDate periodoFim) { this.periodoFim = periodoFim; }
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getCredito() { return credito; }
    public void setCredito(BigDecimal credito) { this.credito = credito; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public Metodo getMetodo() { return metodo; }
    public void setMetodo(Metodo metodo) { this.metodo = metodo; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getCartaoBandeira() { return cartaoBandeira; }
    public void setCartaoBandeira(String cartaoBandeira) { this.cartaoBandeira = cartaoBandeira; }
    public String getCartaoFinal() { return cartaoFinal; }
    public void setCartaoFinal(String cartaoFinal) { this.cartaoFinal = cartaoFinal; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public LocalDateTime getVenceEm() { return venceEm; }
    public void setVenceEm(LocalDateTime venceEm) { this.venceEm = venceEm; }
    public String getResponsavel() { return responsavel; }
    public void setResponsavel(String responsavel) { this.responsavel = responsavel; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getPagoEm() { return pagoEm; }
    public void setPagoEm(LocalDateTime pagoEm) { this.pagoEm = pagoEm; }
}
