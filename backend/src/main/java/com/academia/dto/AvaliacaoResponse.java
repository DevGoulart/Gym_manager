package com.academia.dto;

import java.time.LocalDate;

public record AvaliacaoResponse(
        Long id,
        Long alunoId,
        String alunoNome,
        Long professorId,
        String professorNome,
        LocalDate dataAvaliacao,
        Double peso,
        Double altura,
        Double percentualGordura,
        Double cintura,
        Double quadril,
        Double braco,
        Double coxa,
        String observacoes,
        Double imc,
        String classificacaoImc,
        Double variacaoPeso,
        Double variacaoGordura
) {
}
