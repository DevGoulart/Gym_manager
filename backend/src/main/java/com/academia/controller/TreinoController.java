package com.academia.controller;

import com.academia.dto.TreinoRequest;
import com.academia.dto.TreinoResponse;
import com.academia.service.TreinoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/treinos")
public class TreinoController {

    private final TreinoService service;

    public TreinoController(TreinoService service) {
        this.service = service;
    }

    @GetMapping
    public List<TreinoResponse> listar(@RequestParam(required = false) Long alunoId) {
        return service.listar(alunoId);
    }

    @GetMapping("/{id}")
    public TreinoResponse buscar(@PathVariable Long id) {
        return service.buscarResponse(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TreinoResponse criar(@Valid @RequestBody TreinoRequest req) {
        return service.criar(req);
    }

    @PutMapping("/{id}")
    public TreinoResponse atualizar(@PathVariable Long id, @Valid @RequestBody TreinoRequest req) {
        return service.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
