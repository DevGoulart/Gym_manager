package com.academia.security;

import com.academia.repository.SessaoRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Regras de acesso do sistema, todas neste arquivo:
 *  - o site (HTML, CSS, JS) é público, ele mesmo mostra a tela de login;
 *  - login e cadastro de academia são públicos;
 *  - todo o resto de /api exige login;
 *  - usuários e alterações em planos são só para o perfil ADMIN (o DONO da rede também tem esse papel);
 *  - o painel da rede (/api/rede) é só do DONO; transformar a academia em rede, só do ADMIN.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http, SessaoRepository sessaoRepository) throws Exception {
        http
                // Autenticação por token no cabeçalho (não por cookie): CSRF não se aplica
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .headers(h -> h.frameOptions(f -> f.sameOrigin())) // console do H2 usa frames
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/login/2fa", "/api/auth/cadastro").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/rede/converter").hasRole("ADMIN")
                        .requestMatchers("/api/rede/**", "/api/rede").hasRole("DONO")
                        .requestMatchers("/api/usuarios/**").hasRole("ADMIN")
                        .requestMatchers("/api/assinatura/**", "/api/assinatura").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/planos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/planos/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/planos/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                erroJson(res, HttpServletResponse.SC_UNAUTHORIZED, "Faça login para continuar."))
                        .accessDeniedHandler((req, res, ex) ->
                                erroJson(res, HttpServletResponse.SC_FORBIDDEN, "Seu perfil não tem permissão para esta ação.")))
                .addFilterBefore(new TokenFilter(sessaoRepository), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Mesmo formato de erro do GlobalExceptionHandler: {"erro": "mensagem"}. */
    private static void erroJson(HttpServletResponse res, int status, String mensagem) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write("{\"erro\":\"" + mensagem + "\"}");
    }
}
