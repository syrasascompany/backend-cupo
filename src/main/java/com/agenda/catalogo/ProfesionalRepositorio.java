package com.agenda.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProfesionalRepositorio extends JpaRepository<Profesional, Long> {
    List<Profesional> findByEmpresaIdAndActivoTrue(Long empresaId);
    List<Profesional> findByEmpresaId(Long empresaId);
    long countByEmpresaIdAndActivoTrue(Long empresaId);
}
