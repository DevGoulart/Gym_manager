package com.academia.security;

import com.academia.model.Academia;
import com.academia.model.Perfil;
import com.academia.model.Sessao;
import com.academia.model.Usuario;
import com.academia.repository.SessaoRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Lê o cabeçalho "Authorization: Bearer <token>", procura a sessão pelo hash do token
 * e, se ela for válida, registra o usuário logado no SecurityContext.
 */
public class TokenFilter extends OncePerRequestFilter {

    private final SessaoRepository sessaoRepository;

    public TokenFilter(SessaoRepository sessaoRepository) {
        this.sessaoRepository = sessaoRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecalho = request.getHeader("Authorization");
        if (cabecalho != null && cabecalho.startsWith("Bearer ")) {
            String token = cabecalho.substring(7).trim();
            sessaoRepository.findById(Tokens.hash(token))
                    .filter(s -> s.getExpiraEm().isAfter(LocalDateTime.now()))
                    .filter(s -> s.getUsuario().isAtivo() && s.getUsuario().getAcademia().isAtiva())
                    .ifPresent(this::autenticar);
        }
        chain.doFilter(request, response);
    }

    private void autenticar(Sessao s) {
        Usuario u = s.getUsuario();
        UsuarioLogado logado = UsuarioLogado.de(u, academiaEmUso(s), s.getTokenHash());
        var autoridades = new ArrayList<SimpleGrantedAuthority>(List.of(new SimpleGrantedAuthority("ROLE_" + u.getPerfil().name())));
        // Dentro da unidade aberta, o dono tem os mesmos poderes de um administrador
        if (u.getPerfil() == Perfil.DONO) autoridades.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(logado, null, autoridades);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /**
     * A academia em que a pessoa age: a do cadastro dela. Só o dono de uma rede pode estar em outra,
     * e só se for uma unidade ativa da própria rede (conferido a cada requisição).
     */
    static Academia academiaEmUso(Sessao s) {
        Usuario u = s.getUsuario();
        Academia casa = u.getAcademia();
        Academia aberta = s.getAcademiaAtiva();
        if (u.getPerfil() != Perfil.DONO || aberta == null || !aberta.isAtiva()) return casa;
        boolean mesmaRede = casa.getRede() != null && aberta.getRede() != null
                && Objects.equals(casa.getRede().getId(), aberta.getRede().getId());
        return mesmaRede ? aberta : casa;
    }
}
