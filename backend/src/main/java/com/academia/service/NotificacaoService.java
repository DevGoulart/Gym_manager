package com.academia.service;

import com.academia.dto.AssinaturaDtos.AssinaturaStatus;
import com.academia.dto.NotificacaoDtos.NotificacaoResponse;
import com.academia.dto.NotificacaoDtos.NotificacoesResponse;
import com.academia.exception.NaoAutenticadoException;
import com.academia.model.*;
import com.academia.model.Notificacao.Nivel;
import com.academia.model.Notificacao.Tipo;
import com.academia.repository.*;
import com.academia.security.AcademiaAtual;
import com.academia.security.UsuarioLogado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Área de notificações do topo. Junta dois tipos de aviso:
 *  - gravados (eventos, ver {@link Notificador}): pagamento aprovado, senha alterada, duas etapas...;
 *  - calculados agora, a partir do estado atual da academia aberta:
 *      FINANCEIRO  mensalidades atrasadas e mensalidades que vencem hoje (todos os perfis);
 *      ALUNOS      aniversariantes do dia (todos);
 *      ASSINATURA  teste no fim, assinatura vencendo ou vencida, Pix/boleto aguardando, perto do limite de alunos (administradores);
 *      REDE        unidade sem administrador e todas as vagas em uso (dono da rede);
 *      SEGURANCA   verificação em duas etapas desligada (cada pessoa, uma vez).
 * Os calculados têm uma chave com a data (ou o número) para voltarem a aparecer quando fizer sentido;
 * o que a pessoa já leu fica em notificacoes_lidas.
 */
@Service
public class NotificacaoService {

    private static final int DIAS_HISTORICO = 30;

    private final NotificacaoRepository notificacoes;
    private final NotificacaoLidaRepository lidas;
    private final MensalidadeRepository mensalidades;
    private final AlunoRepository alunos;
    private final UsuarioRepository usuarios;
    private final AcademiaRepository academias;
    private final AssinaturaService assinaturas;
    private final AcademiaAtual academiaAtual;

    public NotificacaoService(NotificacaoRepository notificacoes, NotificacaoLidaRepository lidas,
                              MensalidadeRepository mensalidades, AlunoRepository alunos, UsuarioRepository usuarios,
                              AcademiaRepository academias, AssinaturaService assinaturas, AcademiaAtual academiaAtual) {
        this.notificacoes = notificacoes;
        this.lidas = lidas;
        this.mensalidades = mensalidades;
        this.alunos = alunos;
        this.usuarios = usuarios;
        this.academias = academias;
        this.assinaturas = assinaturas;
        this.academiaAtual = academiaAtual;
    }

    /** Aviso calculado (antes de saber se já foi lido). */
    private record Aviso(String chave, Tipo tipo, Nivel nivel, String titulo, String texto, String link, LocalDateTime data) {
    }

    private static String plural(long n, String um, String varios) {
        return n + " " + (n == 1 ? um : varios);
    }

    @Transactional
    public NotificacoesResponse listar() {
        UsuarioLogado u = academiaAtual.usuario();
        lidas.apagarAntigas(u.id(), LocalDateTime.now().minusDays(90));
        Set<String> jaLidas = lidas.findByUsuarioId(u.id()).stream().map(NotificacaoLida::getChave).collect(Collectors.toSet());
        List<NotificacaoResponse> itens = new ArrayList<>();
        for (Aviso a : calcular(u)) {
            itens.add(new NotificacaoResponse(a.chave(), a.tipo().name(), a.nivel().name(), a.titulo(), a.texto(), a.link(), a.data(), jaLidas.contains(a.chave())));
        }
        for (Notificacao n : notificacoes.findTop30ByUsuarioIdAndCriadaEmAfterOrderByCriadaEmDescIdDesc(u.id(), LocalDateTime.now().minusDays(DIAS_HISTORICO))) {
            itens.add(new NotificacaoResponse("evento:" + n.getId(), n.getTipo().name(), n.getNivel().name(), n.getTitulo(), n.getTexto(), n.getLink(), n.getCriadaEm(), n.getLidaEm() != null));
        }
        // Não lidas primeiro; depois as urgentes; depois as mais recentes
        itens.sort(Comparator.comparing(NotificacaoResponse::lida)
                .thenComparing((NotificacaoResponse n) -> !"URGENTE".equals(n.nivel()))
                .thenComparing(NotificacaoResponse::data, Comparator.reverseOrder()));
        return new NotificacoesResponse(itens, itens.stream().filter(n -> !n.lida()).count());
    }

    @Transactional
    public NotificacoesResponse marcarLida(String chave) {
        Long usuarioId = academiaAtual.usuario().id();
        marcar(usuarioId, chave);
        return listar();
    }

    @Transactional
    public NotificacoesResponse marcarTodas() {
        Long usuarioId = academiaAtual.usuario().id();
        listar().itens().stream().filter(n -> !n.lida()).forEach(n -> marcar(usuarioId, n.chave()));
        return listar();
    }

    private void marcar(Long usuarioId, String chave) {
        if (chave.startsWith("evento:")) {
            try {
                notificacoes.findByIdAndUsuarioId(Long.parseLong(chave.substring(7)), usuarioId).ifPresent(n -> {
                    if (n.getLidaEm() == null) { n.setLidaEm(LocalDateTime.now()); notificacoes.save(n); }
                });
            } catch (NumberFormatException e) { /* chave inválida: ignora */ }
            return;
        }
        if (!lidas.existsByUsuarioIdAndChave(usuarioId, chave)) lidas.save(new NotificacaoLida(usuarioId, chave));
    }

    /* ============================== Avisos calculados ============================== */

    private List<Aviso> calcular(UsuarioLogado u) {
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicioDoDia = hoje.atStartOfDay();
        List<Aviso> avisos = new ArrayList<>();
        Long academiaId = u.academiaId();

        // FINANCEIRO
        List<Mensalidade> lista = mensalidades.findByAcademiaIdOrderByDataVencimentoDesc(academiaId);
        List<Mensalidade> atrasadas = lista.stream().filter(m -> m.getStatus() == StatusMensalidade.ATRASADA).toList();
        if (!atrasadas.isEmpty()) {
            BigDecimal total = atrasadas.stream().map(Mensalidade::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            avisos.add(new Aviso("financeiro:atrasadas:" + hoje, Tipo.FINANCEIRO, Nivel.ATENCAO,
                    plural(atrasadas.size(), "mensalidade atrasada", "mensalidades atrasadas"),
                    Notificador.moeda(total) + " em atraso. Cobre os alunos para não acumular.", "#/mensalidades?status=ATRASADA", inicioDoDia));
        }
        List<Mensalidade> vencemHoje = lista.stream().filter(m -> m.getStatus() == StatusMensalidade.PENDENTE && hoje.equals(m.getDataVencimento())).toList();
        if (!vencemHoje.isEmpty()) {
            BigDecimal total = vencemHoje.stream().map(Mensalidade::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            avisos.add(new Aviso("financeiro:hoje:" + hoje, Tipo.FINANCEIRO, Nivel.INFO,
                    vencemHoje.size() == 1 ? "1 mensalidade vence hoje" : vencemHoje.size() + " mensalidades vencem hoje",
                    "Total de " + Notificador.moeda(total) + ".", "#/mensalidades?status=PENDENTE", inicioDoDia));
        }

        // ALUNOS: aniversariantes (29/02 comemora em 28/02 nos anos que não são bissextos)
        MonthDay diaDeHoje = MonthDay.from(hoje);
        List<String> aniversariantes = alunos.findByAcademiaIdOrderByNomeAsc(academiaId).stream()
                .filter(a -> a.isAtivo() && a.getDataNascimento() != null)
                .filter(a -> {
                    MonthDay md = MonthDay.from(a.getDataNascimento());
                    if (md.equals(MonthDay.of(2, 29)) && !Year.isLeap(hoje.getYear())) md = MonthDay.of(2, 28);
                    return md.equals(diaDeHoje);
                })
                .map(a -> a.getNome().split("\\s+")[0] + (a.getNome().contains(" ") ? " " + a.getNome().substring(a.getNome().lastIndexOf(' ') + 1) : ""))
                .toList();
        if (!aniversariantes.isEmpty()) {
            String nomes = aniversariantes.size() <= 3 ? String.join(", ", aniversariantes)
                    : String.join(", ", aniversariantes.subList(0, 3)) + " e mais " + (aniversariantes.size() - 3);
            avisos.add(new Aviso("alunos:aniversario:" + hoje, Tipo.ALUNOS, Nivel.INFO,
                    aniversariantes.size() == 1 ? "Aniversário de aluno hoje" : aniversariantes.size() + " aniversariantes hoje",
                    nomes + ". Que tal mandar os parabéns?", "#/alunos", inicioDoDia));
        }

        // ASSINATURA (administradores)
        if (u.admin()) {
            AssinaturaStatus st = assinaturas.status();
            String linkAss = st.podeGerenciar() ? "#/assinatura" : null;
            switch (st.situacao()) {
                case "TESTE" -> {
                    if (st.podeGerenciar() && st.diasRestantes() <= 3) {
                        avisos.add(new Aviso("assinatura:teste:" + hoje, Tipo.ASSINATURA, Nivel.ATENCAO,
                                st.diasRestantes() <= 1 ? "O teste grátis termina hoje" : "O teste grátis termina em " + st.diasRestantes() + " dias",
                                "Escolha um plano para continuar cadastrando alunos, usuários e unidades.", linkAss, inicioDoDia));
                    }
                }
                case "VENCIDA" -> avisos.add(new Aviso("assinatura:vencida:" + hoje, Tipo.ASSINATURA, Nivel.URGENTE,
                        st.plano() != null ? "Assinatura vencida" : "Teste grátis encerrado",
                        "Novos alunos, usuários e unidades estão bloqueados." + (st.podeGerenciar() ? (st.plano() != null ? " Renove para liberar." : " Escolha um plano para liberar.") : " Fale com o dono da rede."),
                        linkAss, inicioDoDia));
                case "ATIVA" -> {
                    if (st.podeGerenciar() && st.diasRestantes() <= 7) {
                        avisos.add(new Aviso("assinatura:vence:" + hoje, Tipo.ASSINATURA, Nivel.ATENCAO,
                                st.diasRestantes() == 1 ? "A assinatura vence amanhã" : "A assinatura vence em " + st.diasRestantes() + " dias",
                                "Plano " + st.planoNome() + ", válido até " + st.vigenteAte().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".", linkAss, inicioDoDia));
                    }
                    Integer limite = st.limites().alunos();
                    if (st.podeGerenciar() && limite != null && st.uso().alunosAtivos() >= Math.ceil(limite * 0.9)) {
                        avisos.add(new Aviso("assinatura:limite-alunos:" + hoje, Tipo.ASSINATURA, Nivel.ATENCAO,
                                "Perto do limite de alunos",
                                st.uso().alunosAtivos() + " de " + limite + " alunos ativos no plano " + st.planoNome() + ". Mude de plano para matricular mais.", linkAss, inicioDoDia));
                    }
                }
                default -> { }
            }
            if (st.podeGerenciar() && st.pendente() != null) {
                var p = st.pendente();
                avisos.add(new Aviso("assinatura:pendente:" + p.id(), Tipo.ASSINATURA, Nivel.INFO,
                        ("PIX".equals(p.metodo()) ? "Pix" : "Boleto") + " de " + Notificador.moeda(p.valor()) + " aguardando pagamento",
                        p.descricao() + ".", "#/checkout?pagamento=" + p.id(), p.criadoEm()));
            }
        }

        // REDE (dono)
        if (u.dono() && u.redeId() != null) {
            List<Academia> unidades = academias.findByRedeIdOrderByIdAsc(u.redeId());
            for (Academia un : unidades) {
                long admins = usuarios.countByAcademiaIdAndPerfilAndAtivoTrue(un.getId(), Perfil.ADMIN)
                        + usuarios.countByAcademiaIdAndPerfilAndAtivoTrue(un.getId(), Perfil.DONO);
                if (admins == 0) {
                    avisos.add(new Aviso("rede:sem-admin:" + un.getId(), Tipo.REDE, Nivel.ATENCAO, "Unidade sem administrador",
                            "A " + un.getNome() + " não tem administrador ativo. Cadastre um no painel da rede.", "#/rede", un.getCriadaEm()));
                }
            }
            Rede rede = unidades.isEmpty() ? null : unidades.get(0).getRede();
            if (rede != null && unidades.size() >= rede.getLimiteUnidades()) {
                avisos.add(new Aviso("rede:vagas-cheias:" + rede.getLimiteUnidades(), Tipo.REDE, Nivel.INFO, "Todas as vagas de unidade em uso",
                        "A rede usa as " + rede.getLimiteUnidades() + " vagas. Para abrir outra unidade, compre mais vagas.", "#/assinatura", inicioDoDia));
            }
        }

        // SEGURANCA
        Usuario eu = usuarios.findById(u.id()).orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
        if (!eu.doisFatoresAtivo()) {
            avisos.add(new Aviso("seguranca:2fa-desligada", Tipo.SEGURANCA, Nivel.INFO, "Proteja a sua conta",
                    "Ative a verificação em duas etapas em Meu perfil: o login passa a pedir também um código do celular.", "#/perfil",
                    eu.getCriadoEm() != null ? eu.getCriadoEm() : inicioDoDia));
        }
        return avisos;
    }
}
