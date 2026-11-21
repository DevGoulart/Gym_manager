package com.academia.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Área de notificações do topo. */
public final class NotificacaoDtos {

    private NotificacaoDtos() {
    }

    /**
     * chave: identifica o aviso ("evento:12" para os gravados; "financeiro:atrasadas:2026-11-21" para os calculados).
     * tipo: FINANCEIRO, ALUNOS, ASSINATURA, REDE ou SEGURANCA (cada um com o seu som no navegador).
     * nivel: INFO, ATENCAO ou URGENTE.
     */
    public record NotificacaoResponse(String chave, String tipo, String nivel, String titulo, String texto,
                                      String link, LocalDateTime data, boolean lida) {
    }

    public record NotificacoesResponse(List<NotificacaoResponse> itens, long naoLidas) {
    }

    public record MarcarLidaRequest(@NotBlank(message = "é obrigatória") @Size(max = 150) String chave) {
    }
}
