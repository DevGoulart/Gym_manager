package com.academia.controller;

import com.academia.dto.GerarMensalidadesRequest;
import com.academia.dto.MensalidadeRequest;
import com.academia.dto.MensalidadeResponse;
import com.academia.dto.PagamentoRequest;
import com.academia.model.StatusMensalidade;
import com.academia.service.MensalidadeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mensalidades")
public class MensalidadeController {

    private final MensalidadeService service;

    public MensalidadeController(MensalidadeService service) {
        this.service = service;
    }

    /** GET /api/mensalidades?alunoId=1&status=ATRASADA */
    @GetMapping
    public List<MensalidadeResponse> listar(@RequestParam(required = false) Long alunoId,
                                            @RequestParam(required = false) StatusMensalidade status) {
        return service.listar(alunoId, status);
    }

    @GetMapping("/{id}")
    public MensalidadeResponse buscar(@PathVariable Long id) {
        return service.buscarResponse(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MensalidadeResponse criar(@Valid @RequestBody MensalidadeRequest req) {
        return service.criar(req);
    }

    @PutMapping("/{id}")
    public MensalidadeResponse atualizar(@PathVariable Long id, @Valid @RequestBody MensalidadeRequest req) {
        return service.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }

    /** POST /api/mensalidades/5/pagar  {"formaPagamento": "PIX"} */
    @PostMapping("/{id}/pagar")
    public MensalidadeResponse pagar(@PathVariable Long id, @RequestBody(required = false) PagamentoRequest req) {
        return service.pagar(id, req);
    }

    @PostMapping("/{id}/estornar")
    public MensalidadeResponse estornar(@PathVariable Long id) {
        return service.estornar(id);
    }

    /** POST /api/mensalidades/gerar  {"alunoId": 1, "meses": 3} */
    @PostMapping("/gerar")
    @ResponseStatus(HttpStatus.CREATED)
    public List<MensalidadeResponse> gerar(@Valid @RequestBody GerarMensalidadesRequest req) {
        return service.gerar(req);
    }
}
