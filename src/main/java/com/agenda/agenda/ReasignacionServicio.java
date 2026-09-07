package com.agenda.agenda;

import com.agenda.catalogo.Profesional;
import com.agenda.common.*;
import com.agenda.disponibilidad.DisponibilidadServicio;
import com.agenda.disponibilidad.MotorDisponibilidad.Cupo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import com.agenda.catalogo.ServicioProfesionalRepositorio;
import com.agenda.catalogo.ProfesionalRepositorio;

/**
 * Qué hacer cuando una profesional amanece enferma.
 *
 * En vez de que el dueño llame a cada clienta, el sistema le muestra
 * qué citas quedaron sueltas y a quién le caben, y él resuelve en una
 * sola pantalla.
 */
@Service
@RequiredArgsConstructor
public class ReasignacionServicio {

    private final CitaRepositorio citas;
    private final ProfesionalRepositorio profesionales;
    private final ServicioProfesionalRepositorio serviciosProfesional;
    private final DisponibilidadServicio disponibilidad;
    private final CitaServicio citaServicio;

    /** Una cita afectada y las salidas posibles para ella. */
    public record CitaAfectada(Cita cita, List<Alternativa> alternativas) {}

    public record Alternativa(Long profesionalId, String profesionalNombre,
                              LocalDateTime inicio, boolean mismaHora) {}

    /**
     * Citas de esa profesional ese día, con las opciones para cada una.
     * Se marcan primero las que otra persona puede atender a la MISMA hora:
     * esas no obligan a molestar a la clienta.
     */
    public List<CitaAfectada> revisar(Long profesionalId, LocalDate fecha) {
        Long empresaId = ContextoEmpresa.actual();
        ZoneId zona = disponibilidad.zonaDeEmpresa(empresaId);

        Instant desde = fecha.atStartOfDay(zona).toInstant();
        Instant hasta = fecha.plusDays(1).atStartOfDay(zona).toInstant();

        List<Cita> afectadas = citas
                .findByEmpresaIdAndProfesionalIdAndInicioBetweenOrderByInicio(
                        empresaId, profesionalId, desde, hasta)
                .stream()
                .filter(c -> c.getEstado() == EstadoCita.CONFIRMADA)
                .toList();

        Map<Long, String> nombres = new HashMap<>();
        for (Profesional p : profesionales.findByEmpresaIdAndActivoTrue(empresaId)) {
            nombres.put(p.getId(), p.getNombre());
        }

        List<CitaAfectada> resultado = new ArrayList<>();
        for (Cita cita : afectadas) {
            LocalDateTime horaOriginal = LocalDateTime.ofInstant(cita.getInicio(), zona);

            List<Alternativa> alternativas = disponibilidad
                    .cupos(cita.getServicioId(), fecha, null).stream()
                    .filter(cupo -> !cupo.profesionalId().equals(profesionalId))
                    .map(cupo -> new Alternativa(
                            cupo.profesionalId(),
                            nombres.getOrDefault(cupo.profesionalId(), cupo.profesionalNombre()),
                            cupo.inicio(),
                            cupo.inicio().equals(horaOriginal)))
                    // Primero las de la misma hora, después las más cercanas
                    .sorted(Comparator
                            .comparing(Alternativa::mismaHora).reversed()
                            .thenComparing(a -> Math.abs(Duration.between(
                                    horaOriginal, a.inicio()).toMinutes())))
                    .limit(8)
                    .toList();

            resultado.add(new CitaAfectada(cita, alternativas));
        }
        return resultado;
    }

    /** Pasa la cita a otra profesional, validando que de verdad le quepa. */
    @Transactional
    public Cita reasignar(Long citaId, Long nuevoProfesionalId, LocalDateTime nuevoInicio) {
        Long empresaId = ContextoEmpresa.actual();
        Cita cita = citas.findByIdAndEmpresaId(citaId, empresaId)
                .orElseThrow(() -> new NoEncontradoException("Cita no encontrada"));

        boolean lohace = serviciosProfesional.findByServicioId(cita.getServicioId()).stream()
                .anyMatch(sp -> sp.getProfesionalId().equals(nuevoProfesionalId));
        if (!lohace) throw new ReglaNegocioException("Esa profesional no presta ese servicio");

        return citaServicio.reprogramar(citaId, nuevoProfesionalId, nuevoInicio);
    }
}
