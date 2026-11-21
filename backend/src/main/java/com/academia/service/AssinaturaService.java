package com.academia.service;

import com.academia.dto.AssinaturaDtos.*;
import com.academia.exception.NaoAutenticadoException;
import com.academia.exception.RecursoNaoEncontradoException;
import com.academia.exception.RegraNegocioException;
import com.academia.model.*;
import com.academia.model.Assinatura.Situacao;
import com.academia.repository.*;
import com.academia.security.AcademiaAtual;
import com.academia.security.UsuarioLogado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static com.academia.model.PlanoSistema.*;

/**
 * Assinatura do sistema: teste grátis, planos (Essencial, Profissional, Rede), checkout com o
 * gateway de demonstração, vagas extras de unidade para a rede e os limites de cada plano.
 *
 * Regras de cobrança:
 *  - trocar de plano (ou de ciclo) começa um período novo hoje, descontando o que sobrou do período pago;
 *  - pagar o mesmo plano e ciclo com a assinatura ativa é renovação: o novo período começa quando o atual termina;
 *  - vagas extras compradas no meio do período custam o proporcional aos dias que faltam
 *    e passam a fazer parte da renovação (R$ 59 por vaga no mensal, R$ 590 no anual);
 *  - academia avulsa que assina o Rede vira rede na aprovação (o administrador vira o dono).
 *
 * Limites (só com a assinatura ativa; no teste, tudo liberado): alunos ativos e usuários por
 * academia conforme o plano, e unidades da rede = 5 + vagas extras. Vencida (teste ou período
 * pago terminado): novos alunos, usuários e unidades ficam bloqueados; o resto continua funcionando.
 */
@Service
public class AssinaturaService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AssinaturaRepository assinaturas;
    private final PagamentoRepository pagamentos;
    private final AcademiaRepository academias;
    private final RedeRepository redes;
    private final UsuarioRepository usuarios;
    private final AlunoRepository alunos;
    private final SessaoRepository sessoes;
    private final AcademiaAtual academiaAtual;
    private final GatewayDemonstracao gateway;
    private final Notificador notificador;

    public AssinaturaService(AssinaturaRepository assinaturas, PagamentoRepository pagamentos, AcademiaRepository academias,
                             RedeRepository redes, UsuarioRepository usuarios, AlunoRepository alunos,
                             SessaoRepository sessoes, AcademiaAtual academiaAtual, GatewayDemonstracao gateway,
                             Notificador notificador) {
        this.notificador = notificador;
        this.assinaturas = assinaturas;
        this.pagamentos = pagamentos;
        this.academias = academias;
        this.redes = redes;
        this.usuarios = usuarios;
        this.alunos = alunos;
        this.sessoes = sessoes;
        this.academiaAtual = academiaAtual;
        this.gateway = gateway;
    }

    private static LocalDate hoje() {
        return LocalDate.now();
    }

    private static String data(LocalDate d) {
        return d.format(DATA);
    }

    private static String plural(long n, String um, String varios) {
        return n + " " + (n == 1 ? um : varios);
    }

    /* ============================ Assinatura da conta ============================ */

    /** A assinatura que vale para esta academia: a da rede, se for uma unidade; senão a dela. Cria o teste se não existir. */
    Assinatura daAcademia(Academia a) {
        if (a.getRede() != null) {
            return assinaturas.findByRedeId(a.getRede().getId()).orElseGet(() -> nova(null, a.getRede()));
        }
        return assinaturas.findByAcademiaId(a.getId()).orElseGet(() -> nova(a, null));
    }

    private Assinatura nova(Academia a, Rede r) {
        Assinatura s = new Assinatura();
        s.setAcademia(a);
        s.setRede(r);
        s.setTesteAte(hoje().plusDays(DIAS_TESTE - 1)); // 14 dias contando hoje
        return assinaturas.save(s);
    }

    private Academia academia(Long id) {
        return academias.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Academia não encontrada"));
    }

    /** Conta de quem está logado. Só o administrador da academia avulsa ou o dono da rede gerenciam a assinatura. */
    private record Conta(Assinatura assinatura, Academia academia, Rede rede, boolean podeGerenciar) {
    }

    private Conta conta() {
        UsuarioLogado u = academiaAtual.usuario();
        Academia a = academia(u.academiaId());
        boolean pode = a.getRede() == null ? u.perfil() == Perfil.ADMIN : u.perfil() == Perfil.DONO;
        return new Conta(daAcademia(a), a, a.getRede(), pode);
    }

    private Conta contaGerenciavel() {
        Conta c = conta();
        if (!c.podeGerenciar()) throw new RegraNegocioException("A assinatura da rede é gerenciada pelo dono da rede.");
        return c;
    }

    /* ================================== Limites ================================== */

    private static String mensagemVencida(Assinatura s) {
        String acao = " Para continuar cadastrando alunos, usuários e unidades, o administrador precisa ";
        return s.getVigenteAte() == null
                ? "O período de teste terminou em " + data(s.getTesteAte()) + "." + acao + "escolher um plano em Plano e cobrança."
                : "A assinatura venceu em " + data(s.getVigenteAte()) + "." + acao + "renová-la em Plano e cobrança.";
    }

    private static void exigirNaoVencida(Assinatura s) {
        if (s.situacao(hoje()) == Situacao.VENCIDA) throw new RegraNegocioException(mensagemVencida(s));
    }

    /** Cadastros que o vencimento bloqueia (ex.: nova unidade, novo administrador de unidade). */
    @Transactional
    public void exigirCadastroLiberado(Long academiaId) {
        exigirNaoVencida(daAcademia(academia(academiaId)));
    }

    /** Antes de matricular (ou reativar) um aluno. */
    @Transactional
    public void exigirVagaAluno(Long academiaId) {
        Assinatura s = daAcademia(academia(academiaId));
        exigirNaoVencida(s);
        if (s.situacao(hoje()) != Situacao.ATIVA) return; // teste: liberado
        Integer max = s.getPlano().getMaxAlunos();
        if (max != null && alunos.countByAcademiaIdAndAtivoTrue(academiaId) >= max) {
            throw new RegraNegocioException("O plano " + s.getPlano().getNome() + " permite até " + max
                    + " alunos ativos. Para matricular mais, o administrador pode mudar de plano em Plano e cobrança.");
        }
    }

    /** Antes de criar um usuário na academia. */
    @Transactional
    public void exigirVagaUsuario(Long academiaId) {
        Assinatura s = daAcademia(academia(academiaId));
        exigirNaoVencida(s);
        if (s.situacao(hoje()) != Situacao.ATIVA) return;
        Integer max = s.getPlano().getMaxUsuarios();
        if (max != null && usuarios.countByAcademiaIdAndPerfilNot(academiaId, Perfil.DONO) >= max) {
            throw new RegraNegocioException("O plano " + s.getPlano().getNome() + " permite até " + max
                    + " usuários. Para cadastrar mais, o administrador pode mudar de plano em Plano e cobrança.");
        }
    }

    /** "Transformar em rede" sem pagar só durante o teste; depois, pelo checkout do plano Rede. */
    @Transactional
    public void exigirPodeConverter(Academia a) {
        if (daAcademia(a).situacao(hoje()) != Situacao.TESTE) {
            throw new RegraNegocioException("Para transformar a academia em rede, assine o plano Rede em Plano e cobrança.");
        }
    }

    /** A academia virou rede durante o teste: a assinatura passa a ser da rede. */
    @Transactional
    public void moverParaRede(Academia a, Rede r) {
        assinaturas.findByAcademiaId(a.getId()).ifPresent(s -> {
            s.setAcademia(null);
            s.setRede(r);
            assinaturas.save(s);
        });
    }

    /* ================================== Situação ================================== */

    @Transactional
    public AssinaturaStatus status() {
        Conta c = conta();
        Assinatura s = c.assinatura();
        LocalDate hoje = hoje();
        Situacao sit = s.situacao(hoje);
        long dias = switch (sit) {
            case ATIVA -> ChronoUnit.DAYS.between(hoje, s.getVigenteAte()) + 1;
            case TESTE -> ChronoUnit.DAYS.between(hoje, s.getTesteAte()) + 1;
            case VENCIDA -> 0;
        };
        BigDecimal renovacao = s.getPlano() != null && s.getCiclo() != null
                ? s.getPlano().preco(s.getCiclo()).add(precoUnidadeExtra(s.getCiclo()).multiply(BigDecimal.valueOf(s.getUnidadesExtras())))
                : null;
        PagamentoResponse pendente = pagamentos.findByAssinaturaIdAndStatus(s.getId(), Pagamento.Status.PENDENTE).stream()
                .findFirst().map(p -> PagamentoResponse.de(p, true)).orElse(null);
        List<PagamentoResponse> historico = pagamentos.findTop12ByAssinaturaIdOrderByCriadoEmDescIdDesc(s.getId()).stream()
                .map(p -> PagamentoResponse.de(p, false)).toList();
        return new AssinaturaStatus(c.rede() != null ? "REDE" : "ACADEMIA",
                c.rede() != null ? c.rede().getNome() : c.academia().getNome(), c.podeGerenciar(),
                s.getPlano() != null ? s.getPlano().name() : null, s.getPlano() != null ? s.getPlano().getNome() : null,
                s.getCiclo() != null ? s.getCiclo().name() : null, sit.name(), s.getTesteAte(), s.getInicioPeriodo(),
                s.getVigenteAte(), dias, s.getUnidadesExtras(), limites(s, sit, c.rede() != null), uso(c),
                Arrays.stream(PlanoSistema.values()).map(PlanoCatalogo::de).toList(), UNIDADE_EXTRA_MENSAL,
                UNIDADE_EXTRA_ANUAL, MAX_UNIDADES, DIAS_TESTE, renovacao, pendente, historico);
    }

    private Uso uso(Conta c) {
        if (c.rede() == null) {
            Long id = c.academia().getId();
            return new Uso(alunos.countByAcademiaIdAndAtivoTrue(id), usuarios.countByAcademiaIdAndPerfilNot(id, Perfil.DONO), 1);
        }
        List<Academia> unidades = academias.findByRedeIdOrderByIdAsc(c.rede().getId());
        long ativos = 0, pessoas = 0;
        for (Academia u : unidades) {
            ativos += alunos.countByAcademiaIdAndAtivoTrue(u.getId());
            pessoas += usuarios.findByAcademiaIdOrderByNomeAsc(u.getId()).size();
        }
        return new Uso(ativos, pessoas, unidades.size());
    }

    private static Limites limites(Assinatura s, Situacao sit, boolean rede) {
        int unidades = rede ? REDE.getUnidadesIncluidas() + s.getUnidadesExtras() : 1;
        if (sit == Situacao.TESTE || s.getPlano() == null) return new Limites(null, null, unidades);
        return new Limites(s.getPlano().getMaxAlunos(), s.getPlano().getMaxUsuarios(), unidades);
    }

    /* ================================= Orçamentos ================================= */

    /** O pedido cabe na conta? (plano certo para rede/academia, vagas no limite e uso dentro do plano novo) */
    private void validarPedido(Conta c, PlanoSistema plano, int extras, String nomeRede, boolean exigirNomeRede) {
        if (c.rede() != null && plano != REDE) throw new RegraNegocioException("Uma rede só pode assinar o plano Rede.");
        if (plano != REDE && extras > 0) throw new RegraNegocioException("Vagas extras de unidade são só do plano Rede.");
        if (extras < 0 || REDE.getUnidadesIncluidas() + extras > MAX_UNIDADES) {
            throw new RegraNegocioException("A rede pode ter no máximo " + MAX_UNIDADES + " unidades (" + REDE.getUnidadesIncluidas()
                    + " do plano + " + (MAX_UNIDADES - REDE.getUnidadesIncluidas()) + " extras).");
        }
        Uso uso = uso(c);
        if (plano.getMaxAlunos() != null && uso.alunosAtivos() > plano.getMaxAlunos()) {
            throw new RegraNegocioException("A academia tem " + plural(uso.alunosAtivos(), "aluno ativo", "alunos ativos") + " e o plano "
                    + plano.getNome() + " permite até " + plano.getMaxAlunos() + ". Desative alunos ou escolha um plano maior.");
        }
        if (plano.getMaxUsuarios() != null && uso.usuarios() > plano.getMaxUsuarios()) {
            throw new RegraNegocioException("A academia tem " + plural(uso.usuarios(), "usuário", "usuários") + " e o plano "
                    + plano.getNome() + " permite até " + plano.getMaxUsuarios() + ". Exclua usuários ou escolha um plano maior.");
        }
        if (c.rede() != null && uso.unidades() > REDE.getUnidadesIncluidas() + extras) {
            long faltam = uso.unidades() - REDE.getUnidadesIncluidas();
            throw new RegraNegocioException("A rede tem " + uso.unidades() + " unidades: escolha pelo menos " + plural(faltam, "vaga extra", "vagas extras") + ".");
        }
        if (exigirNomeRede && c.rede() == null && plano == REDE && (nomeRede == null || nomeRede.isBlank())) {
            throw new RegraNegocioException("Nome da rede: informe o nome da rede que será criada.");
        }
    }

    private static BigDecimal creditoRestante(Assinatura s, LocalDate hoje) {
        if (s.getValorPeriodo() == null || s.getInicioPeriodo() == null || s.getVigenteAte() == null) return BigDecimal.ZERO;
        long total = ChronoUnit.DAYS.between(s.getInicioPeriodo(), s.getVigenteAte()) + 1;
        long restantes = ChronoUnit.DAYS.between(hoje, s.getVigenteAte()); // o dia de hoje já foi usado
        if (restantes <= 0 || total <= 0) return BigDecimal.ZERO;
        return s.getValorPeriodo().multiply(BigDecimal.valueOf(restantes)).divide(BigDecimal.valueOf(total), 2, RoundingMode.DOWN);
    }

    private static String descricaoPlano(PlanoSistema plano, CicloCobranca ciclo, int extras) {
        return "Plano " + plano.getNome() + " " + (ciclo == CicloCobranca.ANUAL ? "anual" : "mensal")
                + (extras > 0 ? " + " + plural(extras, "vaga extra de unidade", "vagas extras de unidade") : "");
    }

    private Orcamento calcular(Assinatura s, PlanoSistema plano, CicloCobranca ciclo, int extras) {
        LocalDate hoje = hoje();
        Situacao sit = s.situacao(hoje);
        BigDecimal valorPlano = plano.preco(ciclo);
        BigDecimal valorExtras = precoUnidadeExtra(ciclo).multiply(BigDecimal.valueOf(extras));
        BigDecimal subtotal = valorPlano.add(valorExtras);
        boolean renovacao = sit == Situacao.ATIVA && plano == s.getPlano() && ciclo == s.getCiclo() && extras == s.getUnidadesExtras();
        LocalDate inicio = renovacao ? s.getVigenteAte().plusDays(1) : hoje;
        BigDecimal credito = !renovacao && sit == Situacao.ATIVA ? creditoRestante(s, hoje).min(subtotal) : BigDecimal.ZERO;
        return new Orcamento("PLANO", plano.name(), ciclo.name(), extras, 0, valorPlano, valorExtras, subtotal, credito,
                subtotal.subtract(credito), inicio, ciclo.fimDoPeriodo(inicio), renovacao, descricaoPlano(plano, ciclo, extras));
    }

    @Transactional
    public Orcamento orcamento(PlanoSistema plano, CicloCobranca ciclo, Integer extras) {
        if (plano == null || ciclo == null) throw new RegraNegocioException("Escolha o plano e a forma de cobrança.");
        Conta c = contaGerenciavel();
        int n = extras == null ? 0 : extras;
        validarPedido(c, plano, n, null, false);
        return calcular(c.assinatura(), plano, ciclo, n);
    }

    private void validarUnidades(Conta c, int quantidade) {
        Assinatura s = c.assinatura();
        if (c.rede() == null) throw new RegraNegocioException("Vagas de unidade são do plano Rede.");
        Situacao sit = s.situacao(hoje());
        if (sit == Situacao.TESTE) {
            throw new RegraNegocioException("Durante o teste a rede tem " + REDE.getUnidadesIncluidas() + " unidades. Assine o plano Rede para comprar vagas extras.");
        }
        if (sit == Situacao.VENCIDA) throw new RegraNegocioException("A assinatura venceu: renove o plano Rede antes de comprar vagas.");
        int podeComprar = MAX_UNIDADES - REDE.getUnidadesIncluidas() - s.getUnidadesExtras();
        if (podeComprar <= 0) throw new RegraNegocioException("A rede já tem o máximo de " + MAX_UNIDADES + " unidades.");
        if (quantidade < 1 || quantidade > podeComprar) {
            throw new RegraNegocioException("A rede pode ter no máximo " + MAX_UNIDADES + " unidades: dá para comprar de 1 a " + plural(podeComprar, "vaga", "vagas") + ".");
        }
    }

    /** Vagas no meio do período: o preço cheio das vagas, proporcional aos dias que faltam (contando hoje). */
    private Orcamento calcularUnidades(Assinatura s, int quantidade) {
        LocalDate hoje = hoje();
        CicloCobranca ciclo = s.getCiclo();
        BigDecimal cheio = precoUnidadeExtra(ciclo).multiply(BigDecimal.valueOf(quantidade));
        long total = ChronoUnit.DAYS.between(s.getInicioPeriodo(), s.getVigenteAte()) + 1;
        long restantes = ChronoUnit.DAYS.between(hoje, s.getVigenteAte()) + 1;
        BigDecimal valor = cheio.multiply(BigDecimal.valueOf(Math.min(restantes, total)))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP).max(new BigDecimal("0.01"));
        String descricao = plural(quantidade, "vaga extra de unidade", "vagas extras de unidade") + " (proporcional até " + data(s.getVigenteAte()) + ")";
        return new Orcamento("UNIDADES", REDE.name(), ciclo.name(), s.getUnidadesExtras() + quantidade, quantidade,
                BigDecimal.ZERO, cheio, cheio, cheio.subtract(valor), valor, hoje, s.getVigenteAte(), false, descricao);
    }

    @Transactional
    public Orcamento orcamentoUnidades(Integer quantidade) {
        Conta c = contaGerenciavel();
        int n = quantidade == null ? 1 : quantidade;
        validarUnidades(c, n);
        return calcularUnidades(c.assinatura(), n);
    }

    /* ================================= Pagamentos ================================= */

    @Transactional
    public PagamentoResponse checkout(CheckoutRequest req) {
        Conta c = contaGerenciavel();
        int extras = req.unidadesExtras() == null ? 0 : req.unidadesExtras();
        validarPedido(c, req.plano(), extras, req.nomeRede(), true);
        Orcamento o = calcular(c.assinatura(), req.plano(), req.ciclo(), extras);
        Pagamento p = new Pagamento();
        p.setTipo(Pagamento.Tipo.PLANO);
        p.setPlano(req.plano());
        p.setCiclo(req.ciclo());
        p.setUnidadesExtras(extras);
        if (c.rede() == null && req.plano() == REDE) p.setNomeRede(req.nomeRede().trim());
        p.setRenovacao(o.renovacao());
        p.setPeriodoInicio(o.inicio());
        p.setPeriodoFim(o.fim());
        p.setDescricao(o.descricao());
        p.setSubtotal(o.subtotal());
        p.setCredito(o.credito());
        p.setValor(o.total());
        return processar(c, p, req.metodo(), req.cartao());
    }

    @Transactional
    public PagamentoResponse comprarUnidades(UnidadesRequest req) {
        Conta c = contaGerenciavel();
        validarUnidades(c, req.quantidade());
        Orcamento o = calcularUnidades(c.assinatura(), req.quantidade());
        Pagamento p = new Pagamento();
        p.setTipo(Pagamento.Tipo.UNIDADES);
        p.setPlano(REDE);
        p.setCiclo(c.assinatura().getCiclo());
        p.setUnidadesExtras(req.quantidade());
        p.setPeriodoInicio(o.inicio());
        p.setPeriodoFim(o.fim());
        p.setDescricao(o.descricao());
        p.setSubtotal(o.subtotal());
        p.setCredito(o.credito());
        p.setValor(o.total());
        return processar(c, p, req.metodo(), req.cartao());
    }

    private PagamentoResponse processar(Conta c, Pagamento p, Pagamento.Metodo metodo, CartaoRequest cartao) {
        if (metodo == null || metodo == Pagamento.Metodo.CREDITO) throw new RegraNegocioException("Escolha cartão, Pix ou boleto.");
        Assinatura s = c.assinatura();
        p.setAssinatura(s);
        p.setResponsavel(academiaAtual.usuario().nome());
        p.setStatus(Pagamento.Status.PENDENTE);
        // cartão: valida antes de gravar qualquer coisa (erro no cartão não deixa pedido para trás)
        GatewayDemonstracao.CartaoAprovado aprovado = null;
        if (p.getValor().signum() == 0) metodo = Pagamento.Metodo.CREDITO; // o crédito cobre tudo
        else if (metodo == Pagamento.Metodo.CARTAO) aprovado = gateway.cobrarCartao(cartao, p.getValor());
        // um pedido pendente por vez: o novo substitui o anterior
        pagamentos.findByAssinaturaIdAndStatus(s.getId(), Pagamento.Status.PENDENTE).forEach(x -> x.setStatus(Pagamento.Status.CANCELADO));
        p.setMetodo(metodo);
        switch (metodo) {
            case CARTAO -> {
                p.setCartaoBandeira(aprovado.bandeira());
                p.setCartaoFinal(aprovado.finalNumero());
                aprovar(c, pagamentos.save(p));
            }
            case CREDITO -> aprovar(c, pagamentos.save(p));
            case PIX -> {
                p.setVenceEm(LocalDateTime.now().plusMinutes(30));
                p = pagamentos.save(p);
                p.setCodigo(gateway.gerarPix(p.getValor(), p.getId()));
            }
            case BOLETO -> {
                LocalDate vencimento = hoje().plusDays(3);
                p.setVenceEm(vencimento.atTime(23, 59, 59));
                p = pagamentos.save(p);
                p.setCodigo(gateway.gerarBoleto(p.getValor(), vencimento));
            }
        }
        return PagamentoResponse.de(p, true);
    }

    private Pagamento doPedido(Conta c, Long id) {
        return pagamentos.findByIdAndAssinaturaId(id, c.assinatura().getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento " + id + " não encontrado"));
    }

    @Transactional
    public PagamentoResponse pagamento(Long id) {
        return PagamentoResponse.de(doPedido(contaGerenciavel(), id), true);
    }

    /** Desiste de um Pix ou boleto ainda não pago (para escolher outra forma de pagamento). */
    @Transactional
    public void cancelar(Long id) {
        Pagamento p = doPedido(contaGerenciavel(), id);
        if (p.getStatus() != Pagamento.Status.PENDENTE) throw new RegraNegocioException("Só dá para cancelar um pagamento pendente.");
        p.setStatus(Pagamento.Status.CANCELADO);
        pagamentos.save(p);
    }

    /**
     * Demonstração: faz o papel do banco avisando que o Pix ou o boleto foi pago
     * (num gateway real, isto chegaria por webhook). Confere de novo se o pedido ainda cabe.
     */
    @Transactional
    public PagamentoResponse simularCompensacao(Long id) {
        Conta c = contaGerenciavel();
        Pagamento p = doPedido(c, id);
        if (p.getStatus() == Pagamento.Status.APROVADO) return PagamentoResponse.de(p, false);
        if (p.getStatus() == Pagamento.Status.CANCELADO) throw new RegraNegocioException("Este pedido foi cancelado ou substituído por outro.");
        if (p.getVenceEm() != null && LocalDateTime.now().isAfter(p.getVenceEm())) {
            throw new RegraNegocioException(p.getMetodo() == Pagamento.Metodo.PIX ? "Este Pix expirou. Gere um novo pagamento." : "Este boleto venceu. Gere um novo pagamento.");
        }
        if (p.getTipo() == Pagamento.Tipo.PLANO) validarPedido(c, p.getPlano(), p.getUnidadesExtras(), p.getNomeRede(), false);
        else validarUnidades(c, p.getUnidadesExtras());
        aprovar(c, p);
        return PagamentoResponse.de(p, false);
    }

    private void aprovar(Conta c, Pagamento p) {
        Assinatura s = c.assinatura();
        LocalDate hoje = hoje();
        if (p.getTipo() == Pagamento.Tipo.PLANO) {
            // as datas valem do momento da aprovação (um Pix pode ser pago depois de criado)
            boolean renovacao = s.situacao(hoje) == Situacao.ATIVA && p.getPlano() == s.getPlano()
                    && p.getCiclo() == s.getCiclo() && p.getUnidadesExtras() == s.getUnidadesExtras();
            LocalDate inicio = renovacao ? s.getVigenteAte().plusDays(1) : hoje;
            LocalDate fim = p.getCiclo().fimDoPeriodo(inicio);
            if (renovacao) {
                s.setValorPeriodo((s.getValorPeriodo() == null ? BigDecimal.ZERO : s.getValorPeriodo()).add(p.getSubtotal()));
            } else {
                s.setInicioPeriodo(hoje);
                s.setValorPeriodo(p.getSubtotal());
            }
            s.setVigenteAte(fim);
            s.setPlano(p.getPlano());
            s.setCiclo(p.getCiclo());
            s.setUnidadesExtras(p.getUnidadesExtras());
            p.setRenovacao(renovacao);
            p.setPeriodoInicio(inicio);
            p.setPeriodoFim(fim);
            if (c.rede() == null && p.getPlano() == REDE) converterEmRede(c, s, p.getNomeRede());
        } else {
            s.setUnidadesExtras(s.getUnidadesExtras() + p.getUnidadesExtras());
            s.setValorPeriodo((s.getValorPeriodo() == null ? BigDecimal.ZERO : s.getValorPeriodo()).add(p.getValor()));
            p.setPeriodoInicio(hoje);
            p.setPeriodoFim(s.getVigenteAte());
        }
        if (s.getRede() != null) {
            s.getRede().setLimiteUnidades(REDE.getUnidadesIncluidas() + s.getUnidadesExtras());
            redes.save(s.getRede());
        }
        p.setStatus(Pagamento.Status.APROVADO);
        p.setPagoEm(LocalDateTime.now());
        pagamentos.save(p);
        assinaturas.save(s);
        notificador.pagamentoAprovado(s, p);
    }

    /** Academia avulsa assinou o Rede: cria a rede, a academia vira a 1ª unidade e o administrador vira o dono. */
    private void converterEmRede(Conta c, Assinatura s, String nomeRede) {
        UsuarioLogado logado = academiaAtual.usuario();
        Usuario u = usuarios.findById(logado.id()).orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
        if (u.getPerfil() != Perfil.ADMIN) throw new RegraNegocioException("Somente o administrador da academia pode assinar o plano Rede.");
        Academia a = c.academia();
        Rede r = redes.save(new Rede(nomeRede == null || nomeRede.isBlank() ? a.getNome() : nomeRede.trim()));
        a.setRede(r);
        academias.save(a);
        u.setPerfil(Perfil.DONO);
        usuarios.save(u);
        sessoes.findById(logado.sessaoId()).ifPresent(ses -> { ses.setAcademiaAtiva(a); sessoes.save(ses); });
        s.setAcademia(null);
        s.setRede(r);
    }
}
