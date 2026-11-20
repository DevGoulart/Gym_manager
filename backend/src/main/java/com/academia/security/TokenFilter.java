package com.academia.security;

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
import java.util.List;

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
                    .map(Sessao::getUsuario)
                    .filter(u -> u.isAtivo() && u.getAcademia().isAtiva())
                    .ifPresent(this::autenticar);
        }
        chain.doFilter(request, response);
    }

    private void autenticar(Usuario u) {
        UsuarioLogado logado = UsuarioLogado.de(u);
        var autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + u.getPerfil().name()));
        var auth = new UsernamePasswordAuthenticationToken(logado, null, autoridades);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
