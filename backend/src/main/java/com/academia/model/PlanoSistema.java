package com.academia.model;

import java.math.BigDecimal;

/**
 * Planos de assinatura do sistema Força Total (os mesmos da página de apresentação).
 * Não confundir com {@link Plano}, que são os planos que a academia vende aos alunos.
 * Limites nulos significam "ilimitado".
 */
public enum PlanoSistema {

    ESSENCIAL("Essencial", "99.00", "990.00", 150, 2, 1),
    PROFISSIONAL("Profissional", "189.00", "1890.00", 600, null, 1),
    REDE("Rede", "349.00", "3490.00", null, null, Rede.LIMITE_PADRAO);

    /** Vaga extra de unidade (só no plano Rede), por unidade. */
    public static final BigDecimal UNIDADE_EXTRA_MENSAL = new BigDecimal("59.00");
    public static final BigDecimal UNIDADE_EXTRA_ANUAL = new BigDecimal("590.00");
    /** Total de unidades que uma rede pode ter (as 5 do plano + até 15 extras). */
    public static final int MAX_UNIDADES = 20;
    /** Teste grátis, com tudo liberado, para contas novas e para as que já existiam. */
    public static final int DIAS_TESTE = 14;

    private final String nome;
    private final BigDecimal mensal;
    private final BigDecimal anual;
    private final Integer maxAlunos;
    private final Integer maxUsuarios;
    private final int unidadesIncluidas;

    PlanoSistema(String nome, String mensal, String anual, Integer maxAlunos, Integer maxUsuarios, int unidadesIncluidas) {
        this.nome = nome;
        this.mensal = new BigDecimal(mensal);
        this.anual = new BigDecimal(anual);
        this.maxAlunos = maxAlunos;
        this.maxUsuarios = maxUsuarios;
        this.unidadesIncluidas = unidadesIncluidas;
    }

    public String getNome() { return nome; }
    public BigDecimal getMensal() { return mensal; }
    public BigDecimal getAnual() { return anual; }
    public Integer getMaxAlunos() { return maxAlunos; }
    public Integer getMaxUsuarios() { return maxUsuarios; }
    public int getUnidadesIncluidas() { return unidadesIncluidas; }

    public BigDecimal preco(CicloCobranca ciclo) {
        return ciclo == CicloCobranca.ANUAL ? anual : mensal;
    }

    public static BigDecimal precoUnidadeExtra(CicloCobranca ciclo) {
        return ciclo == CicloCobranca.ANUAL ? UNIDADE_EXTRA_ANUAL : UNIDADE_EXTRA_MENSAL;
    }
}
