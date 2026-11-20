package com.academia.controller;

import com.academia.dto.AlunoRequest;
import com.academia.dto.AlunoResponse;
import com.academia.service.AlunoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alunos")
public class AlunoController {

    private final AlunoService service;

    public AlunoController(AlunoService service) {
        this.service = service;
    }

    /** GET /api/alunos?busca=maria  → consulta por nome ou CPF. */
    @GetMapping
    public List<AlunoResponse> listar(@RequestParam(required = false) String busca) {
        return service.listar(busca);
    }

    @GetMapping("/{id}")
    public AlunoResponse buscar(@PathVariable Long id) {
        return service.buscarResponse(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlunoResponse criar(@Valid @RequestBody AlunoRequest req) {
        return service.criar(req);
    }

    @PutMapping("/{id}")
    public AlunoResponse atualizar(@PathVariable Long id, @Valid @RequestBody AlunoRequest req) {
        return service.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
