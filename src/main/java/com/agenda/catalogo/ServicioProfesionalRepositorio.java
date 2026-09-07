package com.agenda.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServicioProfesionalRepositorio extends JpaRepository<ServicioProfesional, Long> {
    List<ServicioProfesional> findByServicioId(Long servicioId);
    List<ServicioProfesional> findByProfesionalId(Long profesionalId);
    void deleteByServicioIdAndProfesionalId(Long servicioId, Long profesionalId);
}
