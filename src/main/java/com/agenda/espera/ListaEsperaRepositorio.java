package com.agenda.espera;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ListaEsperaRepositorio extends JpaRepository<ListaEspera, Long> {

    /**
     * Quién está esperando un cupo que sirva para esta fecha.
     * Se devuelven en orden de llegada: el que lleva más tiempo, primero.
     */
    @Query("""
           select e from ListaEspera e
           where e.empresaId = :empresaId
             and e.servicioId = :servicioId
             and e.estado = com.agenda.espera.EstadoEspera.ESPERANDO
             and :fecha between e.desde and e.hasta
             and (e.profesionalId is null or e.profesionalId = :profesionalId)
           order by e.creadoEn asc
           """)
    List<ListaEspera> candidatos(@Param("empresaId") Long empresaId,
                                 @Param("servicioId") Long servicioId,
                                 @Param("profesionalId") Long profesionalId,
                                 @Param("fecha") LocalDate fecha);

    List<ListaEspera> findByEmpresaIdAndEstadoOrderByCreadoEnAsc(Long empresaId, EstadoEspera estado);

    /** Para saber si quien acaba de agendar venía de la lista de espera. */
    List<ListaEspera> findByEmpresaIdAndTelefonoAndEstado(
            Long empresaId, String telefono, EstadoEspera estado);
}
