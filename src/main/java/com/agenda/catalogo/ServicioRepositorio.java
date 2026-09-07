package com.agenda.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServicioRepositorio extends JpaRepository<Servicio, Long> {
    List<Servicio> findByEmpresaIdAndActivoTrue(Long empresaId);
    List<Servicio> findByEmpresaId(Long empresaId);
}
