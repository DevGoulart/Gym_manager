package com.academia.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record TreinoRequest(
        @NotNull(message = "é obrigatório") Long alunoId,
        Long professorId,
        @NotBlank(message = "é obrigatório") String nome,
        String objetivo,
        LocalDate dataInicio,
        LocalDate dataFim,
        String observacoes,
        @Valid List<ExercicioDTO> exercicios
) {
}
