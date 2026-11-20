package com.academia.dto;

import java.time.LocalDate;
import java.util.List;

public record TreinoResponse(
        Long id,
        Long alunoId,
        String alunoNome,
        Long professorId,
        String professorNome,
        String nome,
        String objetivo,
        LocalDate dataInicio,
        LocalDate dataFim,
        String observacoes,
        List<ExercicioDTO> exercicios,
        boolean vigente
) {
}
