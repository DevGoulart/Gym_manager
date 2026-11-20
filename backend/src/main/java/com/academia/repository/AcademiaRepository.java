package com.academia.repository;

import com.academia.model.Academia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcademiaRepository extends JpaRepository<Academia, Long> {
    Optional<Academia> findFirstByOrderByIdAsc();
    List<Academia> findByRedeIdOrderByIdAsc(Long redeId);
    Optional<Academia> findByIdAndRedeId(Long id, Long redeId);
    long countByRedeId(Long redeId);
}
