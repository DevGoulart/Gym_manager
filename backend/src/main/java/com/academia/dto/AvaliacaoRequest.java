package com.academia.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record AvaliacaoRequest(
        @NotNull(message = "é obrigatório") Long alunoId,
        Long professorId,
        LocalDate dataAvaliacao,
        @NotNull(message = "é obrigatório") @Positive(message = "deve ser maior que zero") Double peso,
        @NotNull(message = "é obrigatório") @Positive(message = "deve ser maior que zero") Double altura,
        Double percentualGordura,
        Double cintura,
        Double quadril,
        Double braco,
        Double coxa,
        String observacoes
) {
}
