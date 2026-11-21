package com.academia.dto;

import com.academia.model.CicloCobranca;
import com.academia.model.Pagamento;
import com.academia.model.PlanoSistema;
import com.academia.security.QrCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Entrada e saída da assinatura do sistema: planos, checkout, pagamentos e vagas de unidade. */
public final class AssinaturaDtos {

    private AssinaturaDtos() {
    }

    /** Dados do cartão: validados e descartados (só bandeira e 4 últimos dígitos são gravados). */
    public record CartaoRequest(String nome, String numero, String validade, String cvv) {
    }

    public record CheckoutRequest(
            @NotNull(message = "é obrigatório") PlanoSistema plano,
            @NotNull(message = "é obrigatório") CicloCobranca ciclo,
            @Min(value = 0, message = "mínimo 0") @Max(value = 15, message = "máximo 15") Integer unidadesExtras,
            String nomeRede,
            @NotNull(message = "é obrigatório") Pagamento.Metodo metodo,
            @Valid CartaoRequest cartao) {
    }

    public record UnidadesRequest(
            @NotNull(message = "é obrigatório") @Min(value = 1, message = "mínimo 1") @Max(value = 15, message = "máximo 15") Integer quantidade,
            @NotNull(message = "é obrigatório") Pagamento.Metodo metodo,
            @Valid CartaoRequest cartao) {
    }

    public record PlanoCatalogo(String id, String nome, BigDecimal mensal, BigDecimal anual,
                                Integer maxAlunos, Integer maxUsuarios, int unidades) {
        public static PlanoCatalogo de(PlanoSistema p) {
            return new PlanoCatalogo(p.name(), p.getNome(), p.getMensal(), p.getAnual(), p.getMaxAlunos(), p.getMaxUsuarios(), p.getUnidadesIncluidas());
        }
    }

    /** Limites que valem agora (nulo = ilimitado) e o que está sendo usado. */
    public record Limites(Integer alunos, Integer usuarios, Integer unidades) {
    }

    public record Uso(long alunosAtivos, long usuarios, long unidades) {
    }

    /** Quanto custa um pedido, já com o crédito do período pago que não foi usado. */
    public record Orcamento(String tipo, String plano, String ciclo, int unidadesExtras, int quantidade,
                            BigDecimal valorPlano, BigDecimal valorExtras, BigDecimal subtotal, BigDecimal credito,
                            BigDecimal total, LocalDate inicio, LocalDate fim, boolean renovacao, String descricao) {
    }

    public record PagamentoResponse(Long id, String tipo, String plano, String ciclo, int unidadesExtras, String descricao, BigDecimal subtotal, BigDecimal credito,
                                    BigDecimal valor, String metodo, String status, String cartaoBandeira, String cartaoFinal,
                                    String codigo, List<String> qr, LocalDateTime venceEm, LocalDateTime criadoEm,
                                    LocalDateTime pagoEm, String responsavel, LocalDate periodoFim) {
        public static PagamentoResponse de(Pagamento p, boolean comQr) {
            List<String> qr = comQr && p.getMetodo() == Pagamento.Metodo.PIX && p.getCodigo() != null ? QrCode.linhas(p.getCodigo()) : null;
            return new PagamentoResponse(p.getId(), p.getTipo().name(), p.getPlano() != null ? p.getPlano().name() : null,
                    p.getCiclo() != null ? p.getCiclo().name() : null, p.getUnidadesExtras(), p.getDescricao(), p.getSubtotal(), p.getCredito(),
                    p.getValor(), p.getMetodo().name(), p.getStatus().name(), p.getCartaoBandeira(), p.getCartaoFinal(),
                    p.getCodigo(), qr, p.getVenceEm(), p.getCriadoEm(), p.getPagoEm(), p.getResponsavel(), p.getPeriodoFim());
        }
    }

    /**
     * Situação da assinatura da conta (academia avulsa ou rede).
     * podeGerenciar: falso para o administrador de uma unidade de rede (quem paga é o dono).
     */
    public record AssinaturaStatus(String escopo, String conta, boolean podeGerenciar, String plano, String planoNome,
                                   String ciclo, String situacao, LocalDate testeAte, LocalDate inicioPeriodo,
                                   LocalDate vigenteAte, long diasRestantes, int unidadesExtras, Limites limites, Uso uso,
                                   List<PlanoCatalogo> catalogo, BigDecimal unidadeExtraMensal, BigDecimal unidadeExtraAnual,
                                   int maxUnidades, int diasTeste, BigDecimal valorRenovacao, PagamentoResponse pendente,
                                   List<PagamentoResponse> pagamentos) {
    }
}
