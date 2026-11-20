package com.academia.security;

import com.academia.model.Academia;
import com.academia.model.Perfil;
import com.academia.model.Usuario;

/**
 * Quem está fazendo a requisição. Fica guardado no SecurityContext durante cada pedido.
 * academiaId é a academia em que a pessoa está agindo agora: a dela, ou, para o dono de uma rede,
 * a unidade que ele abriu nesta sessão. Todos os services filtram os dados por ela.
 */
public record UsuarioLogado(Long id, String nome, String email, Perfil perfil, Long academiaId, String academiaNome,
                            Long redeId, String sessaoId) {

    public static UsuarioLogado de(Usuario u, Academia ativa, String sessaoId) {
        Long redeId = u.getAcademia().getRede() != null ? u.getAcademia().getRede().getId() : null;
        return new UsuarioLogado(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
                ativa.getId(), ativa.getNome(), redeId, sessaoId);
    }

    /** Administrador da academia aberta (o dono da rede também conta). */
    public boolean admin() {
        return perfil == Perfil.ADMIN || perfil == Perfil.DONO;
    }

    public boolean dono() {
        return perfil == Perfil.DONO;
    }
}
