package com.academia.repository;

import com.academia.model.NotificacaoLida;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Avisos calculados que cada pessoa já leu. */
public interface NotificacaoLidaRepository extends JpaRepository<NotificacaoLida, Long> {

    List<NotificacaoLida> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioIdAndChave(Long usuarioId, String chave);

    @Modifying
    @Transactional
    @Query("delete from NotificacaoLida l where l.usuarioId = :usuarioId")
    int apagarDoUsuario(Long usuarioId);

    @Modifying
    @Transactional
    @Query("delete from NotificacaoLida l where l.usuarioId = :usuarioId and l.lidaEm < :antes")
    int apagarAntigas(Long usuarioId, LocalDateTime antes);
}
