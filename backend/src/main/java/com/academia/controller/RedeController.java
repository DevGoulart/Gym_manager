package com.academia.controller;

import com.academia.dto.AuthDtos.AcademiaResponse;
import com.academia.dto.AuthDtos.EuResponse;
import com.academia.dto.AuthDtos.UsuarioResponse;
import com.academia.dto.RedeDtos.*;
import com.academia.service.RedeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Plano Rede. Tudo aqui é do dono da rede, exceto /converter (administrador de academia avulsa). */
@RestController
@RequestMapping("/api/rede")
public class RedeController {

    private final RedeService service;

    public RedeController(RedeService service) {
        this.service = service;
    }

    /** GET /api/rede → a rede, cada unidade com os seus números e administradores, e a soma da rede. */
    @GetMapping
    public PainelRede painel() {
        return service.painel();
    }

    /** POST /api/rede/unidades {"nome", "cidade", "admin": {"nome", "email", "senha"}} → nova unidade com seu administrador. */
    @PostMapping("/unidades")
    @ResponseStatus(HttpStatus.CREATED)
    public UnidadeResumo criarUnidade(@Valid @RequestBody UnidadeRequest req) {
        return service.criarUnidade(req);
    }

    @PutMapping("/unidades/{id}")
    public AcademiaResponse editarUnidade(@PathVariable Long id, @Valid @RequestBody UnidadeEdicaoRequest req) {
        return service.editarUnidade(id, req);
    }

    /** POST /api/rede/unidades/{id}/admins → mais um administrador para a unidade. */
    @PostMapping("/unidades/{id}/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criarAdmin(@PathVariable Long id, @Valid @RequestBody AdminRequest req) {
        return service.criarAdmin(id, req);
    }

    /** POST /api/rede/unidades/{id}/abrir → as próximas requisições desta sessão passam a ser dessa unidade. */
    @PostMapping("/unidades/{id}/abrir")
    public EuResponse abrir(@PathVariable Long id) {
        return service.abrir(id);
    }

    /** POST /api/rede/converter {"nomeRede"} → transforma a academia avulsa em rede; o administrador vira o dono. */
    /** O que a exclusão da unidade vai apagar e o texto de confirmação. */
    @GetMapping("/unidades/{id}/exclusao")
    public ExclusaoUnidadeInfo exclusaoUnidade(@PathVariable Long id) {
        return service.infoExclusao(id);
    }

    /** Exclui a unidade inteira (senha, código das duas etapas e o nome da unidade digitado). */
    @PostMapping("/unidades/{id}/excluir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirUnidade(@PathVariable Long id, @Valid @RequestBody ExcluirUnidadeRequest req) {
        service.excluirUnidade(id, req);
    }

    @PostMapping("/converter")
    public EuResponse converter(@Valid @RequestBody ConverterRedeRequest req) {
        return service.converter(req);
    }
}
