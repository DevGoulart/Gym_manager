package com.academia.repository;

import com.academia.model.FichaTreino;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FichaTreinoRepository extends JpaRepository<FichaTreino, Long> {
    List<FichaTreino> findByAcademiaIdOrderByDataInicioDesc(Long academiaId);
    Optional<FichaTreino> findByIdAndAcademiaId(Long id, Long academiaId);
    long countByAcademiaId(Long academiaId);
    /** O aluno/professor já foi conferido como sendo da academia antes destas chamadas. */
    List<FichaTreino> findByAlunoIdOrderByDataInicioDesc(Long alunoId);
    List<FichaTreino> findByAlunoId(Long alunoId);
    List<FichaTreino> findByProfessorId(Long professorId);
}
