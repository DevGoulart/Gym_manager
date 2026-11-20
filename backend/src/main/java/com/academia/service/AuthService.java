package com.academia.service;

import com.academia.dto.AuthDtos.*;
import com.academia.exception.MuitasTentativasException;
import com.academia.exception.NaoAutenticadoException;
import com.academia.exception.RegraNegocioException;
import com.academia.model.Academia;
import com.academia.model.Perfil;
import com.academia.model.Rede;
import com.academia.model.Sessao;
import com.academia.model.Usuario;
import com.academia.repository.AcademiaRepository;
import com.academia.repository.RedeRepository;
import com.academia.repository.SessaoRepository;
import com.academia.repository.UsuarioRepository;
import com.academia.security.AcademiaAtual;
import com.academia.security.DesafiosDoisFatores;
import com.academia.security.LimiteTentativas;
import com.academia.security.Tokens;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/** Login, cadastro de nova academia, logout e troca de senha. */
@Service
public class AuthService {

    /** Tempo de vida de uma sessão de login. */
    static final Duration DURACAO_SESSAO = Duration.ofHours(12);

    private final UsuarioRepository usuarioRepository;
    private final AcademiaRepository academiaRepository;
    private final SessaoRepository sessaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final LimiteTentativas limiteTentativas;
    private final AcademiaAtual academiaAtual;
    private final DoisFatoresService doisFatoresService;
    private final DesafiosDoisFatores desafios;
    private final RedeRepository redeRepository;
    private final DispositivosConfiaveis dispositivos;
    /** Hash usado quando o e-mail não existe, para o tempo de resposta não revelar quais e-mails têm conta. */
    private final String hashFalso;

    public AuthService(UsuarioRepository usuarioRepository, AcademiaRepository academiaRepository,
                       SessaoRepository sessaoRepository, PasswordEncoder passwordEncoder,
                       LimiteTentativas limiteTentativas, AcademiaAtual academiaAtual,
                       DoisFatoresService doisFatoresService, DesafiosDoisFatores desafios,
                       RedeRepository redeRepository, DispositivosConfiaveis dispositivos) {
        this.usuarioRepository = usuarioRepository;
        this.academiaRepository = academiaRepository;
        this.sessaoRepository = sessaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.limiteTentativas = limiteTentativas;
        this.academiaAtual = academiaAtual;
        this.doisFatoresService = doisFatoresService;
        this.desafios = desafios;
        this.redeRepository = redeRepository;
        this.dispositivos = dispositivos;
        this.hashFalso = passwordEncoder.encode("senha-que-nunca-sera-usada");
    }

    static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public SessaoResponse login(LoginRequest req) {
        String email = normalizarEmail(req.email());
        if (limiteTentativas.bloqueado(email)) {
            throw new MuitasTentativasException("Muitas tentativas erradas. Aguarde 15 minutos e tente de novo.");
        }
        Usuario u = usuarioRepository.findByEmailIgnoreCase(email).orElse(null);
        boolean senhaCerta = passwordEncoder.matches(req.senha(), u != null ? u.getSenhaHash() : hashFalso);
        if (u == null || !senhaCerta) {
            limiteTentativas.registrarFalha(email);
            throw new NaoAutenticadoException("E-mail ou senha incorretos.");
        }
        if (!u.isAtivo()) {
            throw new NaoAutenticadoException("Este usuário está desativado. Fale com o administrador da academia.");
        }
        if (!u.getAcademia().isAtiva()) {
            throw new NaoAutenticadoException("Esta academia está desativada.");
        }
        // Com verificação em duas etapas, a senha certa ainda não abre a sessão: falta o código do celular,
        // a não ser que este navegador tenha sido lembrado nos últimos 30 dias (a senha continua obrigatória).
        // As falhas de senha só são zeradas quando o login termina de verdade.
        if (u.doisFatoresAtivo()) {
            var lembrado = dispositivos.reconhecer(u, req.dispositivo());
            if (lembrado.isPresent()) {
                limiteTentativas.limparFalhas(email);
                return abrirSessao(u).comDispositivo(null, lembrado.get().getExpiraEm());
            }
            return SessaoResponse.pedirCodigo(desafios.criar(u.getId()));
        }
        limiteTentativas.limparFalhas(email);
        return abrirSessao(u);
    }

    /** Segunda etapa: confere o código do aplicativo (ou um código de recuperação) e abre a sessão. */
    @Transactional
    public SessaoResponse loginComCodigo(CodigoLoginRequest req, String userAgent) {
        Long usuarioId = desafios.usuarioDe(req.desafio());
        if (usuarioId == null) {
            throw new NaoAutenticadoException("A verificação expirou. Entre com sua senha de novo.");
        }
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new NaoAutenticadoException("Entre com sua senha de novo."));
        String email = u.getEmail();
        if (limiteTentativas.bloqueado(email)) {
            desafios.encerrar(req.desafio());
            throw new MuitasTentativasException("Muitas tentativas erradas. Aguarde 15 minutos e tente de novo.");
        }
        if (!doisFatoresService.verificarSegundoFator(u, req.codigo())) {
            limiteTentativas.registrarFalha(email); // códigos errados também contam para o bloqueio
            int restam = desafios.registrarErro(req.desafio());
            throw new NaoAutenticadoException(restam > 0
                    ? "Código incorreto. Restam " + restam + " tentativa(s)."
                    : "Código incorreto. Entre com sua senha de novo.");
        }
        usuarioRepository.save(u); // guarda o intervalo usado (ou o código de recuperação gasto)
        desafios.encerrar(req.desafio());
        limiteTentativas.limparFalhas(email);
        SessaoResponse sessao = abrirSessao(u);
        if (Boolean.TRUE.equals(req.lembrar())) {
            var lembrado = dispositivos.lembrar(u, userAgent);
            return sessao.comDispositivo(lembrado.token(), lembrado.expiraEm());
        }
        return sessao;
    }

    /**
     * Cadastro público, já com login no fim:
     *  - academia avulsa: cria a academia e o primeiro administrador;
     *  - rede: cria a rede, a primeira unidade e o dono da rede (que cadastra as outras unidades depois).
     */
    @Transactional
    public SessaoResponse cadastrar(CadastroAcademiaRequest req) {
        String email = normalizarEmail(req.email());
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RegraNegocioException("Já existe uma conta com este e-mail. Use outro ou faça login.");
        }
        Academia academia = new Academia(req.nomeAcademia().trim(), Textos.limpar(req.cidade()));
        if (req.rede()) {
            if (req.nomeRede() == null || req.nomeRede().isBlank()) {
                throw new RegraNegocioException("Informe o nome da rede.");
            }
            academia.setRede(redeRepository.save(new Rede(req.nomeRede().trim())));
        }
        academia = academiaRepository.save(academia);

        Usuario primeiro = new Usuario();
        primeiro.setAcademia(academia);
        primeiro.setNome(req.nome().trim());
        primeiro.setEmail(email);
        primeiro.setSenhaHash(passwordEncoder.encode(req.senha()));
        primeiro.setPerfil(req.rede() ? Perfil.DONO : Perfil.ADMIN);
        return abrirSessao(usuarioRepository.save(primeiro));
    }

    @Transactional
    public void logout(String cabecalhoAuthorization) {
        if (cabecalhoAuthorization != null && cabecalhoAuthorization.startsWith("Bearer ")) {
            sessaoRepository.deleteById(Tokens.hash(cabecalhoAuthorization.substring(7).trim()));
        }
    }

    /** Quem está logado e em qual academia está agindo (para o dono da rede, a unidade aberta). */
    public EuResponse eu() {
        Usuario u = usuarioRepository.findById(academiaAtual.usuario().id())
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
        Academia aberta = academiaRepository.findById(academiaAtual.id()).orElse(u.getAcademia());
        return new EuResponse(UsuarioResponse.de(u), AcademiaResponse.de(aberta), RedeResponse.de(u.getAcademia().getRede()));
    }

    /** "Meu perfil": troca o próprio nome. */
    @Transactional
    public UsuarioResponse atualizarPerfil(AtualizarPerfilRequest req) {
        Usuario u = usuarioRepository.findById(academiaAtual.usuario().id())
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
        u.setNome(req.nome().trim());
        return UsuarioResponse.de(usuarioRepository.save(u));
    }

    public SessoesResponse sessoes() {
        return new SessoesResponse(sessaoRepository.countByUsuarioIdAndExpiraEmAfter(academiaAtual.usuario().id(), LocalDateTime.now()));
    }

    /** Sai de todos os outros navegadores (ex.: esqueceu a conta aberta no computador da recepção). */
    @Transactional
    public SessoesResponse encerrarOutrasSessoes() {
        var logado = academiaAtual.usuario();
        sessaoRepository.apagarOutrasDoUsuario(logado.id(), logado.sessaoId());
        return sessoes();
    }

    @Transactional
    public void trocarSenha(TrocarSenhaRequest req) {
        Usuario u = usuarioRepository.findById(academiaAtual.usuario().id())
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
        if (!passwordEncoder.matches(req.senhaAtual(), u.getSenhaHash())) {
            throw new RegraNegocioException("A senha atual está incorreta.");
        }
        u.setSenhaHash(passwordEncoder.encode(req.novaSenha()));
        usuarioRepository.save(u);
        // Senha nova: os navegadores lembrados voltam a pedir o código das duas etapas
        dispositivos.esquecerTodos(u.getId());
    }

    private SessaoResponse abrirSessao(Usuario u) {
        LocalDateTime agora = LocalDateTime.now();
        sessaoRepository.apagarExpiradas(agora);
        String token = Tokens.novo();
        LocalDateTime expira = agora.plus(DURACAO_SESSAO);
        Sessao sessao = new Sessao(Tokens.hash(token), u, agora, expira);
        // O dono da rede começa na unidade do cadastro dele; depois troca pelo painel da rede
        if (u.getPerfil() == Perfil.DONO) sessao.setAcademiaAtiva(u.getAcademia());
        sessaoRepository.save(sessao);
        return new SessaoResponse(token, expira, UsuarioResponse.de(u), AcademiaResponse.de(u.getAcademia()), false, null,
                RedeResponse.de(u.getAcademia().getRede()), null, null);
    }
}
