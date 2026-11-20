package com.academia.dto;

import java.time.LocalDate;

public record AlunoResponse(
        Long id,
        String nome,
        String cpf,
        String email,
        String telefone,
        LocalDate dataNascimento,
        LocalDate dataMatricula,
        Long planoId,
        String planoNome,
        boolean ativo,
        String situacaoFinanceira
) {
}
