package com.agenda.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CitaRepositorio extends JpaRepository<Cita, Long> {

    @Query("""
           select c from Cita c
           where c.empresaId = :empresaId
             and c.inicio < :hasta and c.fin > :desde
             and c.estado in (com.agenda.agenda.EstadoCita.CONFIRMADA,
                              com.agenda.agenda.EstadoCita.FINALIZADA)
           order by c.inicio
           """)
    List<Cita> vivasEnRango(@Param("empresaId") Long empresaId,
                            @Param("desde") Instant desde,
                            @Param("hasta") Instant hasta);

    @Query("""
           select c from Cita c
           where c.profesionalId in :profesionales
             and c.inicio < :hasta and c.fin > :desde
             and c.estado in (com.agenda.agenda.EstadoCita.CONFIRMADA,
                              com.agenda.agenda.EstadoCita.FINALIZADA)
           """)
    List<Cita> vivasDeProfesionales(@Param("profesionales") List<Long> profesionales,
                                    @Param("desde") Instant desde,
                                    @Param("hasta") Instant hasta);

    List<Cita> findByEmpresaIdAndProfesionalIdAndInicioBetweenOrderByInicio(
            Long empresaId, Long profesionalId, Instant desde, Instant hasta);

    Optional<Cita> findByIdAndEmpresaId(Long id, Long empresaId);

    @Query("""
           select count(c) from Cita c
           where c.profesionalId = :profesionalId
             and c.inicio >= :desde
             and c.estado = com.agenda.agenda.EstadoCita.CONFIRMADA
           """)
    long contarFuturasDeProfesional(@Param("profesionalId") Long profesionalId,
                                    @Param("desde") Instant desde);
}
