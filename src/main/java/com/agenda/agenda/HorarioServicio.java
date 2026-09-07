package com.agenda.agenda;

import com.agenda.catalogo.CatalogoServicio;
import com.agenda.common.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HorarioServicio {

    private final HorarioRepositorio horarios;
    private final ExcepcionRepositorio excepciones;
    private final CatalogoServicio catalogo;

    public List<HorarioBase> deProfesional(Long profesionalId) {
        catalogo.miProfesional(profesionalId);      // valida que sea de esta empresa
        return horarios.findByProfesionalId(profesionalId);
    }

    public record Tramo(short diaSemana, LocalTime horaInicio, LocalTime horaFin) {}

    /**
     * Reemplaza el horario completo de la profesional.
     * Es más simple de usar que editar tramo por tramo: el panel manda
     * la semana entera y el backend la deja igualita.
     */
    @Transactional
    public List<HorarioBase> reemplazar(Long profesionalId, List<Tramo> tramos) {
        catalogo.miProfesional(profesionalId);

        for (Tramo t : tramos) {
            if (t.diaSemana() < 1 || t.diaSemana() > 7)
                throw new ReglaNegocioException("Día de semana inválido: " + t.diaSemana());
            if (!t.horaFin().isAfter(t.horaInicio()))
                throw new ReglaNegocioException("La hora de fin debe ser mayor a la de inicio");
        }
        // Dos tramos del mismo día no se pueden pisar
        for (int i = 0; i < tramos.size(); i++) {
            for (int j = i + 1; j < tramos.size(); j++) {
                Tramo a = tramos.get(i), b = tramos.get(j);
                if (a.diaSemana() == b.diaSemana()
                        && a.horaInicio().isBefore(b.horaFin())
                        && b.horaInicio().isBefore(a.horaFin())) {
                    throw new ReglaNegocioException("Hay dos tramos encimados el mismo día");
                }
            }
        }

        horarios.deleteByProfesionalId(profesionalId);
        List<HorarioBase> nuevos = tramos.stream()
                .map(t -> new HorarioBase(null, profesionalId, t.diaSemana(),
                        t.horaInicio(), t.horaFin()))
                .toList();
        return horarios.saveAll(nuevos);
    }

    // ---------------- Excepciones: permisos, ausencias, cambios ----------------

    public List<ExcepcionHorario> excepciones(Long profesionalId, LocalDate desde, LocalDate hasta) {
        catalogo.miProfesional(profesionalId);
        return excepciones.findByProfesionalIdAndFechaBetween(profesionalId, desde, hasta);
    }

    /** Sellar la agenda: lo que el dueño hace cuando una chica pide permiso. */
    @Transactional
    public ExcepcionHorario sellar(Long profesionalId, LocalDate fecha, TipoExcepcion tipo,
                                   LocalTime desde, LocalTime hasta, String motivo) {
        catalogo.miProfesional(profesionalId);

        if (tipo != TipoExcepcion.AUSENCIA) {
            if (desde == null || hasta == null)
                throw new ReglaNegocioException("Indique la hora de inicio y de fin");
            if (!hasta.isAfter(desde))
                throw new ReglaNegocioException("La hora de fin debe ser mayor a la de inicio");
        }
        return excepciones.save(new ExcepcionHorario(null, profesionalId, fecha, tipo,
                desde, hasta, motivo));
    }

    @Transactional
    public void quitarExcepcion(Long id) {
        ExcepcionHorario e = excepciones.findById(id)
                .orElseThrow(() -> new ReglaNegocioException("Excepción no encontrada"));
        catalogo.miProfesional(e.getProfesionalId());
        excepciones.delete(e);
    }
}
