package com.academia.service;

import com.academia.dto.AuthDtos.*;
import com.academia.exception.NaoAutenticadoException;
import com.academia.exception.RegraNegocioException;
import com.academia.model.Usuario;
import com.academia.repository.UsuarioRepository;
import com.academia.security.AcademiaAtual;
import com.academia.security.QrCode;
import com.academia.security.Tokens;
import com.academia.security.Totp;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Verificação em duas etapas da conta logada: ativar (com QR Code), desativar e conferir códigos.
 * Também gera os 8 códigos de recuperação, para quem perder o celular.
 */
@Service
public class DoisFatoresService {

    /** Nome que aparece no aplicativo autenticador. */
    static final String EMISSOR = "Força Total";
    private static final int QTD_CODIGOS = 8;
    /** Sem I, O, 0 e 1, que se confundem ao copiar. */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AcademiaAtual academiaAtual;

    public DoisFatoresService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                              AcademiaAtual academiaAtual) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.academiaAtual = academiaAtual;
    }

    private Usuario eu() {
        return usuarioRepository.findById(academiaAtual.usuario().id())
                .orElseThrow(() -> new NaoAutenticadoException("Faça login para continuar."));
    }

    public DoisFatoresStatus status() {
        Usuario u = eu();
        return new DoisFatoresStatus(u.doisFatoresAtivo(), u.getTotpAtivadoEm(), contarCodigos(u));
    }

    /** Passo 1 da ativação: gera um segredo novo e o QR Code para o aplicativo ler. */
    @Transactional
    public DoisFatoresInicio iniciar() {
        Usuario u = eu();
        if (u.doisFatoresAtivo()) throw new RegraNegocioException("A verificação em duas etapas já está ativa.");
        String segredo = Totp.novoSegredo();
        u.setTotpPendente(segredo);
        usuarioRepository.save(u);
        String uri = Totp.uri(EMISSOR, u.getEmail(), segredo);
        return new DoisFatoresInicio(emGrupos(segredo), uri, QrCode.linhas(uri));
    }

    /** Passo 2 da ativação: o primeiro código do aplicativo confirma que ele foi configurado certo. */
    @Transactional
    public CodigosRecuperacao ativar(CodigoRequest req) {
        Usuario u = eu();
        if (u.doisFatoresAtivo()) throw new RegraNegocioException("A verificação em duas etapas já está ativa.");
        if (u.getTotpPendente() == null) throw new RegraNegocioException("Comece a ativação de novo.");
        long passo = Totp.verificar(u.getTotpPendente(), req.codigo(), null);
        if (passo < 0) {
            throw new RegraNegocioException("Código incorreto. Digite o código que aparece agora no aplicativo "
                    + "e confira se o horário do celular está automático.");
        }
        u.setTotpSegredo(u.getTotpPendente());
        u.setTotpPendente(null);
        u.setTotpAtivo(true);
        u.setTotpUltimoPasso(passo);
        u.setTotpAtivadoEm(LocalDateTime.now());
        List<String> codigos = gerarCodigos(u);
        usuarioRepository.save(u);
        return new CodigosRecuperacao(codigos);
    }

    /** Desligar exige a senha e um código válido: quem só pegou o computador destravado não consegue. */
    @Transactional
    public void desativar(DesativarDoisFatoresRequest req) {
        Usuario u = eu();
        if (!u.doisFatoresAtivo()) throw new RegraNegocioException("A verificação em duas etapas não está ativa.");
        if (!passwordEncoder.matches(req.senha(), u.getSenhaHash())) throw new RegraNegocioException("Senha incorreta.");
        if (!verificarSegundoFator(u, req.codigo())) throw new RegraNegocioException("Código incorreto.");
        limpar(u);
        usuarioRepository.save(u);
    }

    /**
     * Confere o código do aplicativo ou, se não for um, um código de recuperação (que é gasto).
     * Quem chama grava o usuário depois, para guardar o último intervalo usado.
     */
    boolean verificarSegundoFator(Usuario u, String codigo) {
        long passo = Totp.verificar(u.getTotpSegredo(), codigo, u.getTotpUltimoPasso());
        if (passo >= 0) {
            u.setTotpUltimoPasso(passo);
            return true;
        }
        return usarCodigoRecuperacao(u, codigo);
    }

    /** Desliga a verificação em duas etapas do usuário (desativação ou redefinição pelo administrador). */
    static void limpar(Usuario u) {
        u.setTotpAtivo(false);
        u.setTotpSegredo(null);
        u.setTotpPendente(null);
        u.setTotpUltimoPasso(null);
        u.setTotpAtivadoEm(null);
        u.setCodigosRecuperacao(null);
    }

    private boolean usarCodigoRecuperacao(Usuario u, String codigo) {
        String normalizado = normalizarCodigo(codigo);
        if (normalizado.length() != 10 || u.getCodigosRecuperacao() == null) return false;
        List<String> hashes = new ArrayList<>(Arrays.asList(u.getCodigosRecuperacao().split(",")));
        if (!hashes.remove(Tokens.hash(normalizado))) return false;
        u.setCodigosRecuperacao(String.join(",", hashes));
        return true;
    }

    private static List<String> gerarCodigos(Usuario u) {
        List<String> codigos = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < QTD_CODIGOS; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 10; j++) sb.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
            codigos.add(sb.substring(0, 5) + "-" + sb.substring(5));
            hashes.add(Tokens.hash(sb.toString()));
        }
        u.setCodigosRecuperacao(String.join(",", hashes));
        return codigos;
    }

    private static int contarCodigos(Usuario u) {
        String c = u.getCodigosRecuperacao();
        return c == null || c.isBlank() ? 0 : c.split(",").length;
    }

    private static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    /** "ABCDEFGH..." → "ABCD EFGH ..." para digitar no aplicativo sem se perder. */
    private static String emGrupos(String segredo) {
        return segredo.replaceAll("(.{4})(?!$)", "$1 ");
    }
}
