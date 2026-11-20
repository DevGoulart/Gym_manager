package com.academia.repository;

import com.academia.model.Aluno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Todas as consultas recebem a academia: uma academia nunca enxerga os alunos de outra. */
public interface AlunoRepository extends JpaRepository<Aluno, Long> {
    List<Aluno> findByAcademiaIdOrderByNomeAsc(Long academiaId);
    Optional<Aluno> findByIdAndAcademiaId(Long id, Long academiaId);
    boolean existsByAcademiaIdAndCpf(Long academiaId, String cpf);
    boolean existsByAcademiaIdAndCpfAndIdNot(Long academiaId, String cpf, Long id);
    boolean existsByPlanoId(Long planoId);
    long countByAcademiaId(Long academiaId);
    long countByAcademiaIdAndAtivoTrue(Long academiaId);
}
