package com.academia.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ProfessorRequest(
        @NotBlank(message = "é obrigatório") String nome,
        String cpf,
        @Email(message = "inválido") String email,
        String telefone,
        String especialidade,
        String cref
) {
}
