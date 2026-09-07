package com.agenda.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TokenClaveRepositorio extends JpaRepository<TokenClave, Long> {
    Optional<TokenClave> findByToken(String token);
}
