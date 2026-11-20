package com.academia.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record AlunoRequest(
        @NotBlank(message = "é obrigatório") String nome,
        String cpf,
        @Email(message = "inválido") String email,
        String telefone,
        LocalDate dataNascimento,
        LocalDate dataMatricula,
        Long planoId,
        Boolean ativo
) {
}
