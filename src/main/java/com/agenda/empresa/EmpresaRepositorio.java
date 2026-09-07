package com.agenda.empresa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EmpresaRepositorio extends JpaRepository<Empresa, Long> {
    Optional<Empresa> findBySlug(String slug);
    boolean existsBySlug(String slug);
    Optional<Empresa> findByWaPhoneNumberId(String waPhoneNumberId);
}
