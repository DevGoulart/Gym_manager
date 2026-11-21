package com.academia.model;

import java.time.LocalDate;

/** De quanto em quanto tempo a assinatura é cobrada. O anual sai pelo preço de 10 meses. */
public enum CicloCobranca {
    MENSAL, ANUAL;

    /** Último dia de um período que começa em {@code inicio}. */
    public LocalDate fimDoPeriodo(LocalDate inicio) {
        return (this == ANUAL ? inicio.plusYears(1) : inicio.plusMonths(1)).minusDays(1);
    }
}
