package com.academia.repository;

import com.academia.model.Professor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfessorRepository extends JpaRepository<Professor, Long> {
    List<Professor> findByAcademiaIdOrderByNomeAsc(Long academiaId);
    Optional<Professor> findByIdAndAcademiaId(Long id, Long academiaId);
    boolean existsByAcademiaIdAndCpf(Long academiaId, String cpf);
    boolean existsByAcademiaIdAndCpfAndIdNot(Long academiaId, String cpf, Long id);
    long countByAcademiaId(Long academiaId);
}
