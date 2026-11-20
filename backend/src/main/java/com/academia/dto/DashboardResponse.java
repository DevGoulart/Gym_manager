package com.academia.dto;

import java.math.BigDecimal;

public record DashboardResponse(
        long alunosAtivos,
        long totalAlunos,
        long professores,
        long planosAtivos,
        long mensalidadesPendentes,
        long mensalidadesAtrasadas,
        BigDecimal recebidoNoMes,
        BigDecimal valorEmAberto,
        long fichasTreino,
        long avaliacoes
) {
}
