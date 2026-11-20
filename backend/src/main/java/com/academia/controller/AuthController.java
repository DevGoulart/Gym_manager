package com.academia.controller;

import com.academia.dto.AuthDtos.*;
import com.academia.service.AuthService;
import com.academia.service.DoisFatoresService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;
    private final DoisFatoresService doisFatores;

    public AuthController(AuthService service, DoisFatoresService doisFatores) {
        this.service = service;
        this.doisFatores = doisFatores;
    }

    /** POST /api/auth/login/2fa {"desafio": "...", "codigo": "123456"} → segunda etapa do login. Público. */
    @PostMapping("/login/2fa")
    public SessaoResponse loginComCodigo(@Valid @RequestBody CodigoLoginRequest req) {
        return service.loginComCodigo(req);
    }

    // ---------- Verificação em duas etapas da própria conta ----------

    @GetMapping("/2fa")
    public DoisFatoresStatus statusDoisFatores() {
        return doisFatores.status();
    }

    /** Gera o segredo e o QR Code para o aplicativo autenticador. */
    @PostMapping("/2fa/iniciar")
    public DoisFatoresInicio iniciarDoisFatores() {
        return doisFatores.iniciar();
    }

    /** Confirma com o primeiro código do aplicativo e devolve os códigos de recuperação (uma única vez). */
    @PostMapping("/2fa/ativar")
    public CodigosRecuperacao ativarDoisFatores(@Valid @RequestBody CodigoRequest req) {
        return doisFatores.ativar(req);
    }

    @PostMapping("/2fa/desativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativarDoisFatores(@Valid @RequestBody DesativarDoisFatoresRequest req) {
        doisFatores.desativar(req);
    }

    /** POST /api/auth/login {"email": "...", "senha": "..."} → token + usuário + academia. Público. */
    @PostMapping("/login")
    public SessaoResponse login(@Valid @RequestBody LoginRequest req) {
        return service.login(req);
    }

    /** POST /api/auth/cadastro → cria a academia e o administrador e já devolve a sessão. Público. */
    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    public SessaoResponse cadastro(@Valid @RequestBody CadastroAcademiaRequest req) {
        return service.cadastrar(req);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        service.logout(authorization);
    }

    /** Quem está logado e de qual academia. */
    @GetMapping("/eu")
    public EuResponse eu() {
        return service.eu();
    }

    @PostMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@Valid @RequestBody TrocarSenhaRequest req) {
        service.trocarSenha(req);
    }
}
