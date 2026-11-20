package com.academia.controller;

import com.academia.dto.AuthDtos.UsuarioRequest;
import com.academia.dto.AuthDtos.UsuarioResponse;
import com.academia.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Usuários da academia. Acesso restrito ao perfil ADMIN (regra no SecurityConfig). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService service;

    public UsuarioController(UsuarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return service.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criar(@Valid @RequestBody UsuarioRequest req) {
        return service.criar(req);
    }

    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest req) {
        return service.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }

    /** Desliga a verificação em duas etapas de alguém que perdeu o celular (ele reativa depois). */
    @PostMapping("/{id}/2fa/redefinir")
    public UsuarioResponse redefinirDoisFatores(@PathVariable Long id) {
        return service.redefinirDoisFatores(id);
    }
}
