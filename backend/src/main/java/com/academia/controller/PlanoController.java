package com.academia.controller;

import com.academia.dto.PlanoRequest;
import com.academia.model.Plano;
import com.academia.service.PlanoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/planos")
public class PlanoController {

    private final PlanoService service;

    public PlanoController(PlanoService service) {
        this.service = service;
    }

    @GetMapping
    public List<Plano> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public Plano buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Plano criar(@Valid @RequestBody PlanoRequest req) {
        return service.criar(req);
    }

    @PutMapping("/{id}")
    public Plano atualizar(@PathVariable Long id, @Valid @RequestBody PlanoRequest req) {
        return service.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
