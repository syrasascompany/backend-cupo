package com.agenda.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HorarioRepositorio extends JpaRepository<HorarioBase, Long> {
    List<HorarioBase> findByProfesionalId(Long profesionalId);
    List<HorarioBase> findByProfesionalIdIn(List<Long> profesionalIds);
    void deleteByProfesionalId(Long profesionalId);
}
