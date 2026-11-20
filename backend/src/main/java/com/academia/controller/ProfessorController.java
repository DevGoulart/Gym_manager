package com.academia.controller;

import com.academia.dto.ProfessorRequest;
import com.academia.model.Professor;
import com.academia.service.ProfessorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/professores")
public class ProfessorController {

    private final ProfessorService service;

    public ProfessorController(ProfessorService service) {
        this.service = service;
    }

    @GetMapping
    public List<Professor> listar(@RequestParam(required = false) String busca) {
        return service.listar(busca);
    }

    @GetMapping("/{id}")
    public Professor buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Professor criar(@Valid @RequestBody ProfessorRequest req) {
        return service.criar(req);
    }

    @PutMapping("/{id}")
    public Professor atualizar(@PathVariable Long id, @Valid @RequestBody ProfessorRequest req) {
        return service.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
