package com.academia.repository;

import com.academia.model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {
    List<Pagamento> findTop12ByAssinaturaIdOrderByCriadoEmDescIdDesc(Long assinaturaId);
    List<Pagamento> findByAssinaturaIdAndStatus(Long assinaturaId, Pagamento.Status status);
    Optional<Pagamento> findByIdAndAssinaturaId(Long id, Long assinaturaId);

    @Modifying
    @Query("delete from Pagamento p where p.assinatura.id = :assinaturaId")
    int apagarDaAssinatura(Long assinaturaId);
}
