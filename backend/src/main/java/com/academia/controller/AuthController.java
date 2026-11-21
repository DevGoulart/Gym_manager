package com.academia.controller;

import com.academia.dto.AuthDtos.*;
import com.academia.service.AuthService;
import com.academia.service.DoisFatoresService;
import com.academia.service.ExclusaoContaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService service;
    private final DoisFatoresService doisFatores;
    private final ExclusaoContaService exclusao;

    public AuthController(AuthService service, DoisFatoresService doisFatores, ExclusaoContaService exclusao) {
        this.exclusao = exclusao;
        this.service = service;
        this.doisFatores = doisFatores;
    }

    /** POST /api/auth/login/2fa {"desafio": "...", "codigo": "123456"} → segunda etapa do login. Público. */
    @PostMapping("/login/2fa")
    public SessaoResponse loginComCodigo(@Valid @RequestBody CodigoLoginRequest req,
                                         @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return service.loginComCodigo(req, userAgent);
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

    /** DELETE /api/auth/2fa/dispositivos/{id} → para de lembrar um navegador. */
    @DeleteMapping("/2fa/dispositivos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void esquecerDispositivo(@PathVariable Long id) {
        doisFatores.esquecerDispositivo(id);
    }

    /** DELETE /api/auth/2fa/dispositivos → para de lembrar todos os navegadores. */
    @DeleteMapping("/2fa/dispositivos")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void esquecerTodosDispositivos() {
        doisFatores.esquecerTodosDispositivos();
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

    /** PUT /api/auth/eu {"nome"} → a própria pessoa troca o nome. */
    @PutMapping("/eu")
    public UsuarioResponse atualizarPerfil(@Valid @RequestBody AtualizarPerfilRequest req) {
        return service.atualizarPerfil(req);
    }

    /** GET /api/auth/sessoes → quantos navegadores estão logados nesta conta agora. */
    @GetMapping("/sessoes")
    public SessoesResponse sessoes() {
        return service.sessoes();
    }

    /** DELETE /api/auth/sessoes/outras → encerra as sessões dos outros navegadores (esta continua). */
    @DeleteMapping("/sessoes/outras")
    public SessoesResponse encerrarOutrasSessoes() {
        return service.encerrarOutrasSessoes();
    }

    /** O que a exclusão da conta vai apagar e o texto de confirmação. */
    @GetMapping("/conta/exclusao")
    public ExclusaoContaInfo exclusaoConta() {
        return exclusao.info();
    }

    /** Exclui a própria conta (e a academia ou a rede, se for o último administrador ou o dono). */
    @PostMapping("/conta/excluir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirConta(@Valid @RequestBody ExcluirContaRequest req) {
        exclusao.excluir(req);
    }

    @PostMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@Valid @RequestBody TrocarSenhaRequest req) {
        service.trocarSenha(req);
    }
}
