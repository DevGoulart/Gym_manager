package com.academia.model;

/** Perfis de acesso de um usuário. */
public enum Perfil {
    /**
     * Dono de uma rede (plano Rede): vê todas as unidades, cria unidades e cadastra os administradores delas.
     * Dentro da unidade que abriu, tem os mesmos poderes de um ADMIN.
     */
    DONO,
    /** Faz tudo, inclusive gerenciar planos e usuários. */
    ADMIN,
    /** Opera alunos, mensalidades, professores, treinos e avaliações; não altera planos nem usuários. */
    FUNCIONARIO
}
