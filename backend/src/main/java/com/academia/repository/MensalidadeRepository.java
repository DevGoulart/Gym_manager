package com.academia.repository;

import com.academia.model.Mensalidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MensalidadeRepository extends JpaRepository<Mensalidade, Long> {
    List<Mensalidade> findByAcademiaIdOrderByDataVencimentoDesc(Long academiaId);
    Optional<Mensalidade> findByIdAndAcademiaId(Long id, Long academiaId);
    /** O aluno já foi conferido como sendo da academia antes destas chamadas. */
    List<Mensalidade> findByAlunoIdOrderByDataVencimentoDesc(Long alunoId);
    boolean existsByAlunoIdAndReferencia(Long alunoId, String referencia);
    void deleteByAlunoId(Long alunoId);
}
