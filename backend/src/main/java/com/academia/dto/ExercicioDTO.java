package com.academia.dto;

import jakarta.validation.constraints.NotBlank;

public record ExercicioDTO(
        @NotBlank(message = "nome do exercício é obrigatório") String nome,
        Integer series,
        String repeticoes,
        String carga
) {
}
