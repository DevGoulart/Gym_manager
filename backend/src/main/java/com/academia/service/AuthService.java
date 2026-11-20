package com.academia.service;

import com.academia.dto.AuthDtos.*;
import com.academia.exception.MuitasTentativasException;
import com.academia.exception.NaoAutenticadoException;
import com.academia.exception.RegraNegocioException;
import com.academia.model.Academia;
import com.academia.model.Perfil;
import com.academia.model.Sessao;
import com.academia.model.Usuario;
import com.academia.repository.AcademiaRepository;
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
    /** Hash usado quando o e-mail não existe, para o tempo de resposta não revelar quais e-mails têm conta. */
    private final String hashFalso;

    public AuthService(UsuarioRepository usuarioRepository, AcademiaRepository academiaRepository,
                       SessaoRepository sessaoRepository, PasswordEncoder passwordEncoder,
                       LimiteTentativas limiteTentativas, AcademiaAtual academiaAtual,
                       DoisFatoresService doisFatoresService, DesafiosDoisFatores desafios) {
        this.usuarioRepository = usuarioRepository;
        this.academiaRepository = academiaRepository;
        this.sessaoRepository = sessaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.limiteTentativas = limiteTentativas;
        this.academiaAtual = academiaAtual;
        this.doisFatoresService = doisFatoresService;
        this.desafios = desafios;
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
        // Com verificação em duas etapas, a senha certa ainda não abre a sessão: falta o código do celular.
        // As falhas de senha só são zeradas quando o login termina de verdade.
        if (u.doisFatoresAtivo()) {
            return SessaoResponse.pedirCodigo(desafios.criar(u.getId()));
        }
        limiteTentativas.limparFalhas(email);
        return abrirSessao(u);
    }

    /** Segunda etapa: confere o código do aplicativo (ou um código de recuperação) e abre a sessão. */
    @Transactional
    public SessaoResponse loginComCodigo(CodigoLoginRequest req) {
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
        return abrirSessao(u);
    }

    /** Cadastro público: cria a academia e o primeiro administrador, e já faz o login. */
    @Transactional
    public SessaoResponse cadastrar(CadastroAcademiaRequest req) {
        String email = normalizarEmail(req.email());
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RegraNegocioException("Já existe uma conta com este e-mail. Use outro ou faça login.");
        }
        Academia academia = academiaRepository.save(new Academia(req.nomeAcademia().trim(), Textos.limpar(req.cidade())));

        Usuario admin = new Usuario();
        admin.setAcademia(academia);
        admin.setNome(req.nome().trim());
        admin.setEmail(email);
        admin.setSenhaHash(passwordEncoder.encode(req.senha()));
        admin.setPerfil(Perfil.ADMIN);
        return abrirSessao(usuarioRepository.save(admin));
    }

    @Transactional
    public void logout(String cabecalhoAuthorization) {
        if (cabecalhoAuthorization != null && cabecalhoAuthorization.startsWith("Bearer ")) {
            sessaoRepository.deleteById(Tokens.hash(cabecalhoAuthorization.substring(7).trim()));
        }
    }

    public EuResponse eu() {
        Usuario u = usuarioRepository.findById(academiaAtual.usuario().id())
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
        return new EuResponse(UsuarioResponse.de(u), AcademiaResponse.de(u.getAcademia()));
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
    }

    private SessaoResponse abrirSessao(Usuario u) {
        LocalDateTime agora = LocalDateTime.now();
        sessaoRepository.apagarExpiradas(agora);
        String token = Tokens.novo();
        LocalDateTime expira = agora.plus(DURACAO_SESSAO);
        sessaoRepository.save(new Sessao(Tokens.hash(token), u, agora, expira));
        return new SessaoResponse(token, expira, UsuarioResponse.de(u), AcademiaResponse.de(u.getAcademia()), false, null);
    }
}
