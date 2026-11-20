package com.academia.repository;

import com.academia.model.DispositivoConfiavel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DispositivoConfiavelRepository extends JpaRepository<DispositivoConfiavel, Long> {

    Optional<DispositivoConfiavel> findByTokenHash(String tokenHash);

    List<DispositivoConfiavel> findByUsuarioIdAndExpiraEmAfterOrderByCriadoEmDesc(Long usuarioId, LocalDateTime agora);

    Optional<DispositivoConfiavel> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Modifying
    @Transactional
    @Query("delete from DispositivoConfiavel d where d.usuario.id = :usuarioId")
    int apagarDoUsuario(Long usuarioId);

    @Modifying
    @Transactional
    @Query("delete from DispositivoConfiavel d where d.expiraEm < :agora")
    int apagarExpirados(LocalDateTime agora);
}
