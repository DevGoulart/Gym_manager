package com.academia.repository;

import com.academia.model.AvaliacaoFisica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AvaliacaoFisicaRepository extends JpaRepository<AvaliacaoFisica, Long> {
    List<AvaliacaoFisica> findByAcademiaIdOrderByDataAvaliacaoDescIdDesc(Long academiaId);
    Optional<AvaliacaoFisica> findByIdAndAcademiaId(Long id, Long academiaId);
    long countByAcademiaId(Long academiaId);
    /** O aluno/professor já foi conferido como sendo da academia antes destas chamadas. */
    List<AvaliacaoFisica> findByAlunoIdOrderByDataAvaliacaoAscIdAsc(Long alunoId);
    List<AvaliacaoFisica> findByAlunoId(Long alunoId);
    List<AvaliacaoFisica> findByProfessorId(Long professorId);
}
