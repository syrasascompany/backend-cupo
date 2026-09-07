package com.agenda.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface ExcepcionRepositorio extends JpaRepository<ExcepcionHorario, Long> {
    List<ExcepcionHorario> findByProfesionalIdInAndFecha(List<Long> profesionalIds, LocalDate fecha);
    List<ExcepcionHorario> findByProfesionalIdAndFechaBetween(
            Long profesionalId, LocalDate desde, LocalDate hasta);
}
