package com.agenda.disponibilidad;

import com.agenda.agenda.*;
import com.agenda.catalogo.*;
import com.agenda.common.ContextoEmpresa;
import com.agenda.common.NoEncontradoException;
import com.agenda.empresa.EmpresaRepositorio;
import com.agenda.disponibilidad.MotorDisponibilidad.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/**
 * Reúne de la base de datos todo lo que el motor necesita y le pide el cálculo.
 * Toda la lógica de horarios vive en el motor; aquí solo se cargan datos.
 */
@Service
@RequiredArgsConstructor
public class DisponibilidadServicio {

    private final ProfesionalRepositorio profesionales;
    private final ServicioRepositorio servicios;
    private final ServicioProfesionalRepositorio serviciosProfesional;
    private final HorarioRepositorio horarios;
    private final ExcepcionRepositorio excepciones;
    private final CitaRepositorio citas;
    private final EmpresaRepositorio empresas;

    private final MotorDisponibilidad motor = new MotorDisponibilidad();

    public ZoneId zonaDeEmpresa(Long empresaId) {
        return empresas.findById(empresaId)
                .map(e -> ZoneId.of(e.getZonaHoraria()))
                .orElseThrow(() -> new NoEncontradoException("Empresa no encontrada"));
    }

    /**
     * @param profesionalPreferida si viene, solo se calculan sus cupos
     */
    public List<Cupo> cupos(Long servicioId, LocalDate fecha, Long profesionalPreferida) {
        Long empresaId = ContextoEmpresa.actual();
        ZoneId zona = zonaDeEmpresa(empresaId);

        // Duración normal del servicio, y el ajuste de quien se sale de la norma
        int duracionNormal = servicios.findById(servicioId)
                .map(Servicio::getDuracionMin)
                .orElseThrow(() -> new NoEncontradoException("Servicio no encontrado"));

        Map<Long, Integer> porProfesional = new HashMap<>();
        for (ServicioProfesional sp : serviciosProfesional.findByServicioId(servicioId)) {
            porProfesional.put(sp.getProfesionalId(),
                    sp.getDuracionMin() != null ? sp.getDuracionMin() : duracionNormal);
        }
        if (porProfesional.isEmpty()) return List.of();

        List<Profesional> activos = profesionales.findByEmpresaIdAndActivoTrue(empresaId).stream()
                .filter(p -> porProfesional.containsKey(p.getId()))
                .filter(p -> profesionalPreferida == null || p.getId().equals(profesionalPreferida))
                .toList();
        if (activos.isEmpty()) return List.of();

        List<Long> ids = activos.stream().map(Profesional::getId).toList();

        Instant desde = fecha.atStartOfDay(zona).toInstant();
        Instant hasta = fecha.plusDays(1).atStartOfDay(zona).toInstant();

        Map<Long, List<HorarioBase>> horariosPorProf = new HashMap<>();
        for (HorarioBase h : horarios.findByProfesionalIdIn(ids)) {
            horariosPorProf.computeIfAbsent(h.getProfesionalId(), k -> new ArrayList<>()).add(h);
        }

        Map<Long, List<ExcepcionHorario>> excepcionesPorProf = new HashMap<>();
        for (ExcepcionHorario e : excepciones.findByProfesionalIdInAndFecha(ids, fecha)) {
            excepcionesPorProf.computeIfAbsent(e.getProfesionalId(), k -> new ArrayList<>()).add(e);
        }

        Map<Long, List<Tramo>> ocupadoPorProf = new HashMap<>();
        for (Cita c : citas.vivasDeProfesionales(ids, desde, hasta)) {
            Tramo t = new Tramo(
                    LocalDateTime.ofInstant(c.getInicio(), zona),
                    LocalDateTime.ofInstant(c.getFin(), zona));
            ocupadoPorProf.computeIfAbsent(c.getProfesionalId(), k -> new ArrayList<>()).add(t);
        }

        List<DatosProfesional> datos = activos.stream()
                .map(p -> new DatosProfesional(
                        p.getId(), p.getNombre(),
                        horariosPorProf.getOrDefault(p.getId(), List.of()),
                        excepcionesPorProf.getOrDefault(p.getId(), List.of()),
                        ocupadoPorProf.getOrDefault(p.getId(), List.of()),
                        porProfesional.get(p.getId())))
                .toList();

        return motor.cuposDelDia(fecha, zona, Instant.now(), datos);
    }

    /** ¿Cabe exactamente esta cita? Se usa antes de crear o reprogramar. */
    public boolean estaLibre(Long servicioId, Long profesionalId, LocalDateTime inicio) {
        return cupos(servicioId, inicio.toLocalDate(), profesionalId).stream()
                .anyMatch(c -> c.inicio().equals(inicio));
    }
}
