package com.academia.dto;

import com.academia.model.StatusMensalidade;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MensalidadeResponse(
        Long id,
        Long alunoId,
        String alunoNome,
        String referencia,
        BigDecimal valor,
        LocalDate dataVencimento,
        LocalDate dataPagamento,
        String formaPagamento,
        StatusMensalidade status
) {
}
