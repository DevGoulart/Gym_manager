package com.academia.controller;

import com.academia.dto.AvaliacaoRequest;
import com.academia.dto.AvaliacaoResponse;
import com.academia.service.AvaliacaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/avaliacoes")
public class AvaliacaoController {

    private final AvaliacaoService service;

    public AvaliacaoController(AvaliacaoService service) {
        this.service = service;
    }

    /** GET /api/avaliacoes?alunoId=1 → histórico (acompanhamento) do aluno. */
    @GetMapping
    public List<AvaliacaoResponse> listar(@RequestParam(required = false) Long alunoId) {
        return service.listar(alunoId);
    }

    @GetMapping("/{id}")
    public AvaliacaoResponse buscar(@PathVariable Long id) {
        return service.buscarResponse(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AvaliacaoResponse criar(@Valid @RequestBody AvaliacaoRequest req) {
        return service.criar(req);
    }

    @PutMapping("/{id}")
    public AvaliacaoResponse atualizar(@PathVariable Long id, @Valid @RequestBody AvaliacaoRequest req) {
        return service.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
