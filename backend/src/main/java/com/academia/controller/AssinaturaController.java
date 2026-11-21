package com.academia.controller;

import com.academia.dto.AssinaturaDtos.*;
import com.academia.exception.RegraNegocioException;
import com.academia.model.CicloCobranca;
import com.academia.model.PlanoSistema;
import com.academia.service.AssinaturaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

/**
 * Assinatura do sistema (planos, checkout com gateway de demonstração e vagas de unidade).
 * Só administradores (regra no SecurityConfig); quem paga é o administrador da academia avulsa ou o dono da rede.
 */
@RestController
@RequestMapping("/api/assinatura")
public class AssinaturaController {

    private final AssinaturaService service;

    public AssinaturaController(AssinaturaService service) {
        this.service = service;
    }

    @GetMapping
    public AssinaturaStatus status() {
        return service.status();
    }

    /** Quanto custa um plano agora (com o crédito do período pago que sobrou). */
    @GetMapping("/orcamento")
    public Orcamento orcamento(@RequestParam String plano, @RequestParam String ciclo, @RequestParam(required = false) Integer extras) {
        return service.orcamento(enumDe(PlanoSistema.class, plano, "Plano"), enumDe(CicloCobranca.class, ciclo, "Forma de cobrança"), extras);
    }

    /** Quanto custam N vagas extras de unidade agora (proporcional ao período). */
    @GetMapping("/orcamento-unidades")
    public Orcamento orcamentoUnidades(@RequestParam(required = false) Integer quantidade) {
        return service.orcamentoUnidades(quantidade);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public PagamentoResponse checkout(@Valid @RequestBody CheckoutRequest req) {
        return service.checkout(req);
    }

    @PostMapping("/unidades")
    @ResponseStatus(HttpStatus.CREATED)
    public PagamentoResponse comprarUnidades(@Valid @RequestBody UnidadesRequest req) {
        return service.comprarUnidades(req);
    }

    @GetMapping("/pagamentos/{id}")
    public PagamentoResponse pagamento(@PathVariable Long id) {
        return service.pagamento(id);
    }

    /** Demonstração: simula o banco confirmando o Pix ou o boleto. */
    @PostMapping("/pagamentos/{id}/simular")
    public PagamentoResponse simular(@PathVariable Long id) {
        return service.simularCompensacao(id);
    }

    @PostMapping("/pagamentos/{id}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable Long id) {
        service.cancelar(id);
    }

    private static <E extends Enum<E>> E enumDe(Class<E> tipo, String valor, String nome) {
        try {
            return Enum.valueOf(tipo, valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RegraNegocioException(nome + " inválido: " + valor);
        }
    }
}
