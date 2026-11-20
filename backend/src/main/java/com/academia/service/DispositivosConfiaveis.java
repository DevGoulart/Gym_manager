package com.academia.service;

import com.academia.exception.RecursoNaoEncontradoException;
import com.academia.model.DispositivoConfiavel;
import com.academia.model.Usuario;
import com.academia.repository.DispositivoConfiavelRepository;
import com.academia.security.Tokens;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * "Lembrar deste dispositivo por 30 dias" da verificação em duas etapas.
 * Regras:
 *  - só dispensa o código do aplicativo; a senha continua sendo pedida em todo login;
 *  - o token só vale para o usuário que o criou e até a data de validade (30 dias, sem renovar sozinho);
 *  - trocar a senha, desligar ou redefinir as duas etapas e excluir o usuário apagam todos os dispositivos dele;
 *  - cada pessoa tem no máximo {@value #MAXIMO_POR_USUARIO} dispositivos lembrados (os mais antigos saem).
 */
@Service
public class DispositivosConfiaveis {

    public static final Duration VALIDADE = Duration.ofDays(30);
    static final int MAXIMO_POR_USUARIO = 10;

    private final DispositivoConfiavelRepository repositorio;

    public DispositivosConfiaveis(DispositivoConfiavelRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Resultado de lembrar um dispositivo: o token vai para o navegador (uma única vez). */
    public record Lembrado(String token, LocalDateTime expiraEm) {
    }

    @Transactional
    public Lembrado lembrar(Usuario u, String userAgent) {
        LocalDateTime agora = LocalDateTime.now();
        repositorio.apagarExpirados(agora);
        String token = Tokens.novo();
        LocalDateTime expira = agora.plus(VALIDADE);
        repositorio.save(new DispositivoConfiavel(Tokens.hash(token), u, descrever(userAgent), agora, expira));
        // Limite por pessoa: os mais antigos deixam de ser lembrados
        List<DispositivoConfiavel> ativos = repositorio.findByUsuarioIdAndExpiraEmAfterOrderByCriadoEmDesc(u.getId(), agora);
        if (ativos.size() > MAXIMO_POR_USUARIO) repositorio.deleteAll(ativos.subList(MAXIMO_POR_USUARIO, ativos.size()));
        return new Lembrado(token, expira);
    }

    /** O navegador mandou um token: vale se for deste usuário e ainda estiver no prazo. */
    @Transactional
    public Optional<DispositivoConfiavel> reconhecer(Usuario u, String token) {
        if (token == null || token.isBlank() || token.length() > 200) return Optional.empty();
        LocalDateTime agora = LocalDateTime.now();
        return repositorio.findByTokenHash(Tokens.hash(token.trim()))
                .filter(d -> Objects.equals(d.getUsuario().getId(), u.getId()))
                .filter(d -> d.getExpiraEm().isAfter(agora))
                .map(d -> { d.setUltimoUsoEm(agora); return repositorio.save(d); });
    }

    public List<DispositivoConfiavel> listar(Long usuarioId) {
        return repositorio.findByUsuarioIdAndExpiraEmAfterOrderByCriadoEmDesc(usuarioId, LocalDateTime.now());
    }

    @Transactional
    public void esquecer(Long usuarioId, Long id) {
        DispositivoConfiavel d = repositorio.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Dispositivo não encontrado"));
        repositorio.delete(d);
    }

    /** Apaga todos os dispositivos lembrados da pessoa (ela volta a digitar o código em todos). */
    @Transactional
    public void esquecerTodos(Long usuarioId) {
        repositorio.apagarDoUsuario(usuarioId);
    }

    /** "Mozilla/5.0 (Windows NT 10.0...) ... Edg/131" → "Edge · Windows". */
    static String descrever(String ua) {
        if (ua == null || ua.isBlank()) return "Navegador desconhecido";
        String navegador = ua.contains("Edg/") ? "Edge"
                : ua.contains("OPR/") || ua.contains("Opera") ? "Opera"
                : ua.contains("Firefox/") ? "Firefox"
                : ua.contains("SamsungBrowser/") ? "Samsung Internet"
                : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Safari/") ? "Safari"
                : "Navegador";
        String sistema = ua.contains("Android") ? "Android"
                : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
                : ua.contains("Windows") ? "Windows"
                : ua.contains("Mac OS X") ? "macOS"
                : ua.contains("Linux") ? "Linux"
                : "sistema desconhecido";
        return navegador + " · " + sistema;
    }
}
