package com.academia.security;

import com.academia.exception.NaoAutenticadoException;
import com.academia.model.Academia;
import jakarta.persistence.EntityManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Dá aos services a academia do usuário logado. É por aqui que todas as consultas
 * e todos os cadastros ficam presos à academia certa.
 */
@Component
public class AcademiaAtual {

    private final EntityManager entityManager;

    public AcademiaAtual(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public UsuarioLogado usuario() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioLogado u) {
            return u;
        }
        throw new NaoAutenticadoException("Faça login para continuar.");
    }

    /** Id da academia em que o usuário logado está agindo (para o dono de uma rede, a unidade aberta). */
    public Long id() {
        return usuario().academiaId();
    }

    /** Rede do usuário logado, ou null se a academia dele não faz parte de uma rede. */
    public Long redeId() {
        return usuario().redeId();
    }

    /** Referência à academia para gravar em novos registros (sem consultar o banco). */
    public Academia referencia() {
        return entityManager.getReference(Academia.class, id());
    }
}
