package com.academia.model;

/** Perfis de acesso de um usuário dentro da sua academia. */
public enum Perfil {
    /** Faz tudo, inclusive gerenciar planos e usuários. */
    ADMIN,
    /** Opera alunos, mensalidades, professores, treinos e avaliações; não altera planos nem usuários. */
    FUNCIONARIO
}
