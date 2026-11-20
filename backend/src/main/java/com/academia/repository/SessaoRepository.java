package com.academia.repository;

import com.academia.model.Sessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface SessaoRepository extends JpaRepository<Sessao, String> {

    @Modifying
    @Transactional
    @Query("delete from Sessao s where s.expiraEm < :agora")
    int apagarExpiradas(LocalDateTime agora);

    @Modifying
    @Transactional
    @Query("delete from Sessao s where s.usuario.id = :usuarioId")
    int apagarDoUsuario(Long usuarioId);
}
