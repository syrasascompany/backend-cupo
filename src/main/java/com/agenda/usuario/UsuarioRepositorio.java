package com.agenda.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<Usuario> findByDocumento(String documento);
    boolean existsByDocumento(String documento);
    Optional<Usuario> findByProfesionalId(Long profesionalId);
    List<Usuario> findByEmpresaId(Long empresaId);
}
