package com.academia.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MensalidadeRequest(
        @NotNull(message = "é obrigatório") Long alunoId,
        String referencia,
        @Positive(message = "deve ser maior que zero") BigDecimal valor,
        @NotNull(message = "é obrigatório") LocalDate dataVencimento,
        LocalDate dataPagamento,
        String formaPagamento
) {
}
