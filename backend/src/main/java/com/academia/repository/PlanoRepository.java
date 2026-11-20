package com.academia.repository;

import com.academia.model.Plano;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanoRepository extends JpaRepository<Plano, Long> {
    List<Plano> findByAcademiaIdOrderByValorMensalAsc(Long academiaId);
    Optional<Plano> findByIdAndAcademiaId(Long id, Long academiaId);
    long countByAcademiaIdAndAtivoTrue(Long academiaId);
}
