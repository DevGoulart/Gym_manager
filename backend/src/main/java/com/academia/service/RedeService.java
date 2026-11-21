package com.academia.service;

import com.academia.dto.AuthDtos.AcademiaResponse;
import com.academia.dto.AuthDtos.EuResponse;
import com.academia.dto.AuthDtos.RedeResponse;
import com.academia.dto.AuthDtos.UsuarioResponse;
import com.academia.dto.DashboardResponse;
import com.academia.dto.RedeDtos.*;
import com.academia.exception.NaoAutenticadoException;
import com.academia.exception.RecursoNaoEncontradoException;
import com.academia.exception.RegraNegocioException;
import com.academia.model.*;
import com.academia.repository.AcademiaRepository;
import com.academia.repository.AlunoRepository;
import com.academia.repository.RedeRepository;
import com.academia.repository.SessaoRepository;
import com.academia.repository.UsuarioRepository;
import com.academia.security.AcademiaAtual;
import com.academia.security.UsuarioLogado;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Plano Rede: o dono enxerga todas as unidades da rede, cada uma separada, cria unidades,
 * cadastra os administradores delas e "abre" a unidade que quer gerenciar.
 * As unidades continuam isoladas entre si: administradores e funcionários só veem a própria.
 * (Acesso a /api/rede: só perfil DONO, regra no SecurityConfig; converter: só ADMIN.)
 */
@Service
public class RedeService {

    private final RedeRepository redeRepository;
    private final AcademiaRepository academiaRepository;
    private final UsuarioRepository usuarioRepository;
    private final SessaoRepository sessaoRepository;
    private final DashboardService dashboardService;
    private final PasswordEncoder passwordEncoder;
    private final AcademiaAtual academiaAtual;
    private final AlunoRepository alunoRepository;
    private final DoisFatoresService doisFatores;
    private final RemocaoDeAcademias remocao;
    private final AssinaturaService assinaturas;

    public RedeService(RedeRepository redeRepository, AcademiaRepository academiaRepository,
                       UsuarioRepository usuarioRepository, SessaoRepository sessaoRepository,
                       DashboardService dashboardService, PasswordEncoder passwordEncoder, AcademiaAtual academiaAtual,
                       AlunoRepository alunoRepository, DoisFatoresService doisFatores, RemocaoDeAcademias remocao,
                       AssinaturaService assinaturas) {
        this.assinaturas = assinaturas;
        this.alunoRepository = alunoRepository;
        this.doisFatores = doisFatores;
        this.remocao = remocao;
        this.redeRepository = redeRepository;
        this.academiaRepository = academiaRepository;
        this.usuarioRepository = usuarioRepository;
        this.sessaoRepository = sessaoRepository;
        this.dashboardService = dashboardService;
        this.passwordEncoder = passwordEncoder;
        this.academiaAtual = academiaAtual;
    }

    /** Rede do dono logado (o filtro de segurança já garante o perfil; aqui garante que a rede existe). */
    private Rede redeDoDono() {
        UsuarioLogado u = academiaAtual.usuario();
        if (!u.dono() || u.redeId() == null) {
            // 422 (e não 401): o site trata 401 como sessão expirada e faria logout
            throw new RegraNegocioException("Apenas o dono da rede acessa esta área.");
        }
        return redeRepository.findById(u.redeId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Rede não encontrada"));
    }

    /** Unidade da rede do dono. Pedir uma unidade de outra rede dá "não encontrada", sem revelar que ela existe. */
    private Academia unidade(Long id, Rede rede) {
        return academiaRepository.findByIdAndRedeId(id, rede.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Unidade " + id + " não encontrada"));
    }

    /** Painel do dono: todas as unidades, cada uma com os seus números, e a soma da rede. */
    public PainelRede painel() {
        Rede rede = redeDoDono();
        UsuarioLogado dono = academiaAtual.usuario();
        Long sede = usuarioRepository.findById(dono.id()).map(u -> u.getAcademia().getId()).orElse(null);
        List<UnidadeResumo> unidades = academiaRepository.findByRedeIdOrderByIdAsc(rede.getId()).stream()
                .map(a -> new UnidadeResumo(a.getId(), a.getNome(), a.getCidade(),
                        a.getId().equals(dono.academiaId()), a.getId().equals(sede),
                        dashboardService.resumo(a.getId()),
                        usuarioRepository.findByAcademiaIdAndPerfilOrderByNomeAsc(a.getId(), Perfil.ADMIN).stream()
                                .map(UsuarioResponse::de).toList(),
                        usuarioRepository.countByAcademiaIdAndPerfilNot(a.getId(), Perfil.DONO)))
                .toList();
        TotaisRede totais = new TotaisRede(unidades.size(),
                unidades.stream().mapToLong(u -> u.numeros().alunosAtivos()).sum(),
                somar(unidades, DashboardResponse::recebidoNoMes),
                unidades.stream().mapToLong(u -> u.numeros().mensalidadesAtrasadas()).sum(),
                somar(unidades, DashboardResponse::valorEmAberto),
                unidades.stream().mapToLong(u -> u.numeros().professores()).sum());
        return new PainelRede(RedeResponse.de(rede), unidades, totais);
    }

    private static BigDecimal somar(List<UnidadeResumo> unidades, java.util.function.Function<DashboardResponse, BigDecimal> campo) {
        return unidades.stream().map(u -> campo.apply(u.numeros())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Nova unidade, já com o seu administrador (cada unidade tem o seu). */
    @Transactional
    public UnidadeResumo criarUnidade(UnidadeRequest req) {
        Rede rede = redeDoDono();
        assinaturas.exigirCadastroLiberado(academiaAtual.id());
        if (academiaRepository.countByRedeId(rede.getId()) >= rede.getLimiteUnidades()) {
            throw new RegraNegocioException("A rede já usa as " + rede.getLimiteUnidades() + " vagas de unidade. Compre mais vagas em Plano e cobrança.");
        }
        Academia a = new Academia(req.nome().trim(), Textos.limpar(req.cidade()));
        a.setRede(rede);
        a = academiaRepository.save(a);
        Usuario admin = novoAdmin(a, req.admin());
        return new UnidadeResumo(a.getId(), a.getNome(), a.getCidade(), false, false, dashboardService.resumo(a.getId()),
                List.of(UsuarioResponse.de(admin)), 1);
    }

    @Transactional
    public AcademiaResponse editarUnidade(Long id, UnidadeEdicaoRequest req) {
        Academia a = unidade(id, redeDoDono());
        a.setNome(req.nome().trim());
        a.setCidade(Textos.limpar(req.cidade()));
        return AcademiaResponse.de(academiaRepository.save(a));
    }

    /** Mais um administrador para uma unidade da rede. */
    @Transactional
    public UsuarioResponse criarAdmin(Long unidadeId, AdminRequest req) {
        Academia a = unidade(unidadeId, redeDoDono());
        assinaturas.exigirCadastroLiberado(a.getId());
        return UsuarioResponse.de(novoAdmin(a, req));
    }

    private Usuario novoAdmin(Academia unidade, AdminRequest req) {
        String email = AuthService.normalizarEmail(req.email());
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RegraNegocioException("Já existe uma conta com o e-mail " + email);
        }
        Usuario u = new Usuario();
        u.setAcademia(unidade);
        u.setNome(req.nome().trim());
        u.setEmail(email);
        u.setPerfil(Perfil.ADMIN);
        u.setSenhaHash(passwordEncoder.encode(req.senha()));
        return usuarioRepository.save(u);
    }

    /** O dono passa a ver e gerenciar esta unidade nesta sessão (todas as telas usam a unidade aberta). */
    @Transactional
    public EuResponse abrir(Long unidadeId) {
        Rede rede = redeDoDono();
        Academia a = unidade(unidadeId, rede);
        if (!a.isAtiva()) throw new RegraNegocioException("Esta unidade está desativada.");
        Sessao s = sessaoRepository.findById(academiaAtual.usuario().sessaoId())
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
        s.setAcademiaAtiva(a);
        sessaoRepository.save(s);
        return new EuResponse(UsuarioResponse.de(s.getUsuario()), AcademiaResponse.de(a), RedeResponse.de(rede));
    }

    /**
     * Unidade que o dono pode excluir. A unidade principal (onde está a conta do dono) não pode:
     * para apagar tudo, o caminho é excluir a conta em "Meu perfil", que leva a rede inteira.
     */
    private Academia unidadeExcluivel(Long id, Rede rede, Usuario dono) {
        Academia a = unidade(id, rede);
        if (a.getId().equals(dono.getAcademia().getId())) {
            throw new RegraNegocioException("A sua unidade principal não pode ser excluída. Para apagar a rede inteira, exclua a sua conta em Meu perfil.");
        }
        return a;
    }

    private Usuario donoLogado() {
        return usuarioRepository.findById(academiaAtual.usuario().id())
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
    }

    /** Prévia da exclusão de uma unidade: o que some e o texto a digitar (o nome da unidade). */
    @Transactional(readOnly = true)
    public ExclusaoUnidadeInfo infoExclusao(Long id) {
        Rede rede = redeDoDono();
        Usuario dono = donoLogado();
        Academia a = unidadeExcluivel(id, rede, dono);
        return new ExclusaoUnidadeInfo(a.getId(), a.getNome(), alunoRepository.countByAcademiaId(a.getId()),
                usuarioRepository.findByAcademiaIdOrderByNomeAsc(a.getId()).size(), dono.doisFatoresAtivo(),
                a.getId().equals(academiaAtual.usuario().academiaId()));
    }

    /**
     * Exclui a unidade com tudo o que há nela: alunos, mensalidades, treinos, avaliações, professores,
     * planos e os usuários (administradores e funcionários perdem o acesso na hora). Mesmo sistema da
     * exclusão da conta: senha, código das duas etapas (se ativa) e o nome da unidade digitado.
     * Se o dono estava com ela aberta, volta para a unidade principal.
     */
    @Transactional
    public void excluirUnidade(Long id, ExcluirUnidadeRequest req) {
        Rede rede = redeDoDono();
        Usuario dono = donoLogado();
        Academia a = unidadeExcluivel(id, rede, dono);
        doisFatores.confirmarIdentidade(dono, req.senha(), req.codigo());
        if (!ExclusaoContaService.confere(req.confirmacao(), a.getNome())) {
            throw new RegraNegocioException("Confirmação incorreta: digite o nome da unidade, como aparece no aviso.");
        }
        remocao.apagar(List.of(a));
    }

    /**
     * Upgrade para o plano Rede: o administrador de uma academia avulsa cria a rede.
     * A academia vira a primeira unidade e ele vira o dono (com os mesmos poderes de admin dentro dela).
     */
    @Transactional
    public EuResponse converter(ConverterRedeRequest req) {
        UsuarioLogado logado = academiaAtual.usuario();
        Usuario u = usuarioRepository.findById(logado.id())
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
        if (u.getPerfil() != Perfil.ADMIN) {
            throw new RegraNegocioException("Somente o administrador da academia pode transformá-la em rede.");
        }
        Academia a = u.getAcademia();
        if (a.getRede() != null) {
            throw new RegraNegocioException("Esta academia já faz parte de uma rede.");
        }
        assinaturas.exigirPodeConverter(a); // sem pagar, só durante o teste
        Rede rede = redeRepository.save(new Rede(req.nomeRede().trim()));
        a.setRede(rede);
        academiaRepository.save(a);
        assinaturas.moverParaRede(a, rede);
        u.setPerfil(Perfil.DONO);
        usuarioRepository.save(u);
        sessaoRepository.findById(logado.sessaoId()).ifPresent(s -> { s.setAcademiaAtiva(a); sessaoRepository.save(s); });
        return new EuResponse(UsuarioResponse.de(u), AcademiaResponse.de(a), RedeResponse.de(rede));
    }
}
