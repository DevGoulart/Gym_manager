package com.academia.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GerarMensalidadesRequest(
        @NotNull(message = "é obrigatório") Long alunoId,
        @Min(value = 1, message = "mínimo 1") @Max(value = 12, message = "máximo 12") Integer meses
) {
}
