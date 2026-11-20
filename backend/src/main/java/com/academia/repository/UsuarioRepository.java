package com.academia.repository;

import com.academia.model.Perfil;
import com.academia.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    List<Usuario> findByAcademiaIdOrderByNomeAsc(Long academiaId);
    Optional<Usuario> findByIdAndAcademiaId(Long id, Long academiaId);
    long countByAcademiaIdAndPerfilAndAtivoTrue(Long academiaId, Perfil perfil);
}
