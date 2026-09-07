package com.agenda.disponibilidad;

import com.agenda.agenda.*;

import java.time.*;
import java.util.*;

/**
 * Calcula los cupos realmente libres.
 *
 * Es el corazón del sistema y está escrito sin dependencias de Spring
 * ni de base de datos a propósito: así se puede probar con datos en
 * memoria, que es la única forma de confiar en él.
 *
 * El orden importa:
 *   1. De qué horario parte la profesional ese día (base o cambio de horario)
 *   2. Se le restan las ausencias y bloqueos
 *   3. Se le restan las citas que ya tiene
 *   4. Sobre lo que queda, se generan los cupos que quepan COMPLETOS
 */
public class MotorDisponibilidad {

    /** Cada cuántos minutos se ofrece un cupo. 15 es lo habitual en belleza. */
    private final int granularidadMin;
    /** Cuánta anticipación mínima se exige para reservar. */
    private final int anticipacionMin;

    public MotorDisponibilidad(int granularidadMin, int anticipacionMin) {
        this.granularidadMin = granularidadMin;
        this.anticipacionMin = anticipacionMin;
    }

    public MotorDisponibilidad() { this(15, 30); }

    /** Un rango de tiempo dentro del día, en hora local del negocio. */
    public record Tramo(LocalDateTime inicio, LocalDateTime fin) {
        public boolean seCruzaCon(Tramo otro) {
            return inicio.isBefore(otro.fin) && otro.inicio.isBefore(fin);
        }
        public long minutos() { return Duration.between(inicio, fin).toMinutes(); }
    }

    /** Un cupo ofrecible a la clienta. */
    public record Cupo(Long profesionalId, String profesionalNombre,
                       LocalDateTime inicio, LocalDateTime fin, int duracionMin) {}

    /** Todo lo que hace falta saber de una profesional para ese día. */
    public record DatosProfesional(
            Long profesionalId,
            String nombre,
            List<HorarioBase> horarios,
            List<ExcepcionHorario> excepciones,
            List<Tramo> citasOcupadas,
            /** Minutos que se demora ESTA persona en el servicio. Nulo si no lo presta. */
            Integer duracionMin
    ) {}

    /**
     * Cupos libres para un servicio en una fecha, mezclando a todas las
     * profesionales que lo prestan y devolviéndolos ordenados por hora.
     */
    public List<Cupo> cuposDelDia(LocalDate fecha, ZoneId zona, Instant ahora,
                                  List<DatosProfesional> profesionales) {
        List<Cupo> resultado = new ArrayList<>();
        for (DatosProfesional p : profesionales) {
            resultado.addAll(cuposDeProfesional(fecha, zona, ahora, p));
        }
        resultado.sort(Comparator
                .comparing(Cupo::inicio)
                .thenComparing(Cupo::profesionalId));
        return resultado;
    }

    public List<Cupo> cuposDeProfesional(LocalDate fecha, ZoneId zona, Instant ahora,
                                         DatosProfesional p) {
        // No presta este servicio: no ofrece nada.
        if (p.duracionMin() == null) return List.of();

        int duracion = p.duracionMin();
        if (duracion <= 0) return List.of();

        List<Tramo> tramos = tramosDeTrabajo(fecha, p);
        if (tramos.isEmpty()) return List.of();

        // Se descuenta lo que ya está tomado: bloqueos y citas.
        List<Tramo> ocupados = new ArrayList<>(p.citasOcupadas());
        ocupados.addAll(bloqueos(fecha, p.excepciones()));

        List<Tramo> libres = restar(tramos, ocupados);

        LocalDateTime minimo = ahora.atZone(zona).toLocalDateTime().plusMinutes(anticipacionMin);

        List<Cupo> cupos = new ArrayList<>();
        for (Tramo libre : libres) {
            LocalDateTime cursor = alinear(libre.inicio());
            while (!cursor.plusMinutes(duracion).isAfter(libre.fin())) {
                if (!cursor.isBefore(minimo)) {
                    cupos.add(new Cupo(p.profesionalId(), p.nombre(),
                            cursor, cursor.plusMinutes(duracion), duracion));
                }
                cursor = cursor.plusMinutes(granularidadMin);
            }
        }
        return cupos;
    }

    /** De qué horario parte ese día: el base, o el cambio si lo hay. Vacío si está ausente. */
    private List<Tramo> tramosDeTrabajo(LocalDate fecha, DatosProfesional p) {
        List<ExcepcionHorario> delDia = p.excepciones().stream()
                .filter(e -> e.getFecha().equals(fecha))
                .toList();

        boolean ausente = delDia.stream().anyMatch(e -> e.getTipo() == TipoExcepcion.AUSENCIA);
        if (ausente) return List.of();

        List<ExcepcionHorario> cambios = delDia.stream()
                .filter(e -> e.getTipo() == TipoExcepcion.CAMBIO_HORARIO)
                .filter(e -> e.getHoraInicio() != null && e.getHoraFin() != null)
                .toList();

        // Un cambio de horario reemplaza al horario normal de ese día.
        if (!cambios.isEmpty()) {
            return cambios.stream()
                    .map(e -> new Tramo(fecha.atTime(e.getHoraInicio()), fecha.atTime(e.getHoraFin())))
                    .sorted(Comparator.comparing(Tramo::inicio))
                    .toList();
        }

        short dia = (short) fecha.getDayOfWeek().getValue();   // 1=lunes ... 7=domingo
        return p.horarios().stream()
                .filter(h -> h.getDiaSemana() == dia)
                .map(h -> new Tramo(fecha.atTime(h.getHoraInicio()), fecha.atTime(h.getHoraFin())))
                .sorted(Comparator.comparing(Tramo::inicio))
                .toList();
    }

    private List<Tramo> bloqueos(LocalDate fecha, List<ExcepcionHorario> excepciones) {
        return excepciones.stream()
                .filter(e -> e.getFecha().equals(fecha))
                .filter(e -> e.getTipo() == TipoExcepcion.BLOQUEO)
                .filter(e -> e.getHoraInicio() != null && e.getHoraFin() != null)
                .map(e -> new Tramo(fecha.atTime(e.getHoraInicio()), fecha.atTime(e.getHoraFin())))
                .toList();
    }

    /** Le quita a los tramos de trabajo todo lo ocupado, dejando los huecos reales. */
    List<Tramo> restar(List<Tramo> base, List<Tramo> ocupados) {
        List<Tramo> resultado = new ArrayList<>(base);

        for (Tramo ocupado : ocupados) {
            List<Tramo> siguiente = new ArrayList<>();
            for (Tramo libre : resultado) {
                if (!libre.seCruzaCon(ocupado)) {
                    siguiente.add(libre);
                    continue;
                }
                // Queda un pedazo antes
                if (libre.inicio().isBefore(ocupado.inicio())) {
                    siguiente.add(new Tramo(libre.inicio(), ocupado.inicio()));
                }
                // Queda un pedazo después
                if (ocupado.fin().isBefore(libre.fin())) {
                    siguiente.add(new Tramo(ocupado.fin(), libre.fin()));
                }
            }
            resultado = siguiente;
        }
        resultado.sort(Comparator.comparing(Tramo::inicio));
        return resultado;
    }

    /** Sube la hora al siguiente múltiplo de la granularidad: 9:07 con paso 15 → 9:15. */
    private LocalDateTime alinear(LocalDateTime momento) {
        int minuto = momento.getMinute();
        int resto = minuto % granularidadMin;
        LocalDateTime base = momento.withSecond(0).withNano(0);
        return resto == 0 ? base : base.plusMinutes(granularidadMin - resto);
    }
}
