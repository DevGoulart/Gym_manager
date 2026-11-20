package com.academia.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PlanoRequest(
        @NotBlank(message = "é obrigatório") String nome,
        String descricao,
        @NotNull(message = "é obrigatório") @Positive(message = "deve ser maior que zero") BigDecimal valorMensal,
        @NotNull(message = "é obrigatório") @Min(value = 1, message = "mínimo 1 mês") Integer duracaoMeses,
        Boolean ativo
) {
}
