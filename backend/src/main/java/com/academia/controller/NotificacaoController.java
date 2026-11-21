package com.academia.controller;

import com.academia.dto.NotificacaoDtos.MarcarLidaRequest;
import com.academia.dto.NotificacaoDtos.NotificacoesResponse;
import com.academia.service.NotificacaoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Área de notificações do topo (qualquer usuário logado vê as suas). */
@RestController
@RequestMapping("/api/notificacoes")
public class NotificacaoController {

    private final NotificacaoService service;

    public NotificacaoController(NotificacaoService service) {
        this.service = service;
    }

    @GetMapping
    public NotificacoesResponse listar() {
        return service.listar();
    }

    @PostMapping("/lidas")
    public NotificacoesResponse marcarLida(@Valid @RequestBody MarcarLidaRequest req) {
        return service.marcarLida(req.chave());
    }

    @PostMapping("/lidas/todas")
    public NotificacoesResponse marcarTodas() {
        return service.marcarTodas();
    }
}
