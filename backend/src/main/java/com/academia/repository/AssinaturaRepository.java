package com.academia.repository;

import com.academia.model.Assinatura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssinaturaRepository extends JpaRepository<Assinatura, Long> {
    Optional<Assinatura> findByAcademiaId(Long academiaId);
    Optional<Assinatura> findByRedeId(Long redeId);
}
