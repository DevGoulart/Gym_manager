package com.academia.security;

import com.academia.model.Perfil;
import com.academia.model.Usuario;

/** Quem está fazendo a requisição. Fica guardado no SecurityContext durante cada pedido. */
public record UsuarioLogado(Long id, String nome, String email, Perfil perfil, Long academiaId, String academiaNome) {

    public static UsuarioLogado de(Usuario u) {
        return new UsuarioLogado(u.getId(), u.getNome(), u.getEmail(), u.getPerfil(),
                u.getAcademia().getId(), u.getAcademia().getNome());
    }

    public boolean admin() {
        return perfil == Perfil.ADMIN;
    }
}
