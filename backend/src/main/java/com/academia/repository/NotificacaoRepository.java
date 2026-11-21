package com.academia.repository;

import com.academia.model.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findTop30ByUsuarioIdAndCriadaEmAfterOrderByCriadaEmDescIdDesc(Long usuarioId, LocalDateTime desde);

    Optional<Notificacao> findByIdAndUsuarioId(Long id, Long usuarioId);

    @Modifying
    @Transactional
    @Query("delete from Notificacao n where n.usuarioId = :usuarioId")
    int apagarDoUsuario(Long usuarioId);
}
