package com.agenda.disponibilidad;

import com.agenda.agenda.*;
import com.agenda.disponibilidad.MotorDisponibilidad.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Casos tomados de la operación real de un spa de uñas.
 * Si estos pasan, el sistema no cruza citas.
 */
class MotorDisponibilidadTest {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final LocalDate MARTES = LocalDate.of(2026, 9, 8);

    private final MotorDisponibilidad motor = new MotorDisponibilidad(15, 30);

    /** El día anterior, para que la anticipación mínima nunca estorbe en las pruebas. */
    private Instant ayer() {
        return MARTES.minusDays(1).atTime(8, 0).atZone(ZONA).toInstant();
    }

    private HorarioBase horario(long profesionalId, int desde, int hasta) {
        return new HorarioBase(null, profesionalId, (short) MARTES.getDayOfWeek().getValue(),
                LocalTime.of(desde, 0), LocalTime.of(hasta, 0));
    }

    private Tramo tramo(int hDesde, int mDesde, int hHasta, int mHasta) {
        return new Tramo(MARTES.atTime(hDesde, mDesde), MARTES.atTime(hHasta, mHasta));
    }

    @Test
    @DisplayName("Un día vacío de 9 a 12 con servicio de 60 min ofrece cupos cada 15 min hasta las 11")
    void diaVacio() {
        var p = new DatosProfesional(1L, "Ana", List.of(horario(1L, 9, 12)),
                List.of(), List.of(), 60);

        List<Cupo> cupos = motor.cuposDeProfesional(MARTES, ZONA, ayer(), p);

        assertEquals(MARTES.atTime(9, 0), cupos.get(0).inicio());
        assertEquals(MARTES.atTime(11, 0), cupos.get(cupos.size() - 1).inicio());
        assertEquals(13, cupos.size());   // 9:00, 9:15 ... 11:00
    }

    @Test
    @DisplayName("Una cita existente parte el día y no se ofrecen cupos encima de ella")
    void noSeOfreceEncimaDeUnaCita() {
        var p = new DatosProfesional(1L, "Ana", List.of(horario(1L, 9, 13)),
                List.of(), List.of(tramo(10, 0, 11, 30)), 60);

        List<Cupo> cupos = motor.cuposDeProfesional(MARTES, ZONA, ayer(), p);

        assertTrue(cupos.stream().noneMatch(c ->
                c.inicio().isBefore(MARTES.atTime(11, 30)) &&
                c.fin().isAfter(MARTES.atTime(10, 0))),
                "ningún cupo puede pisar la cita de 10:00 a 11:30");

        assertTrue(cupos.stream().anyMatch(c -> c.inicio().equals(MARTES.atTime(9, 0))));
        assertTrue(cupos.stream().anyMatch(c -> c.inicio().equals(MARTES.atTime(11, 30))));
    }

    @Test
    @DisplayName("Dos profesionales con duraciones distintas ofrecen cupos distintos para el mismo servicio")
    void duracionesDistintasPorProfesional() {
        var rapida = new DatosProfesional(1L, "Ana", List.of(horario(1L, 9, 11)),
                List.of(), List.of(), 60);
        var lenta  = new DatosProfesional(2L, "Luisa", List.of(horario(2L, 9, 11)),
                List.of(), List.of(), 120);

        List<Cupo> cupos = motor.cuposDelDia(MARTES, ZONA, ayer(), List.of(rapida, lenta));

        long deAna   = cupos.stream().filter(c -> c.profesionalId() == 1L).count();
        long deLuisa = cupos.stream().filter(c -> c.profesionalId() == 2L).count();

        assertEquals(5, deAna);    // 9:00 .. 10:00
        assertEquals(1, deLuisa);  // solo 9:00 le cabe
    }

    @Test
    @DisplayName("Una ausencia deja a la profesional sin cupos ese día")
    void ausencia() {
        var excepcion = new ExcepcionHorario(null, 1L, MARTES,
                TipoExcepcion.AUSENCIA, null, null, "Incapacidad");

        var p = new DatosProfesional(1L, "Ana", List.of(horario(1L, 9, 18)),
                List.of(excepcion), List.of(), 60);

        assertTrue(motor.cuposDeProfesional(MARTES, ZONA, ayer(), p).isEmpty());
    }

    @Test
    @DisplayName("Un permiso de 2 a 4 bloquea solo esa franja")
    void bloqueoParcial() {
        var permiso = new ExcepcionHorario(null, 1L, MARTES, TipoExcepcion.BLOQUEO,
                LocalTime.of(14, 0), LocalTime.of(16, 0), "Permiso");

        var p = new DatosProfesional(1L, "Ana", List.of(horario(1L, 9, 18)),
                List.of(permiso), List.of(), 60);

        List<Cupo> cupos = motor.cuposDeProfesional(MARTES, ZONA, ayer(), p);

        assertTrue(cupos.stream().noneMatch(c ->
                c.inicio().isBefore(MARTES.atTime(16, 0)) &&
                c.fin().isAfter(MARTES.atTime(14, 0))));
        assertTrue(cupos.stream().anyMatch(c -> c.inicio().equals(MARTES.atTime(16, 0))));
    }

    @Test
    @DisplayName("Un cambio de horario reemplaza al horario normal de ese día")
    void cambioDeHorario() {
        var cambio = new ExcepcionHorario(null, 1L, MARTES, TipoExcepcion.CAMBIO_HORARIO,
                LocalTime.of(13, 0), LocalTime.of(17, 0), "Entra más tarde");

        var p = new DatosProfesional(1L, "Ana", List.of(horario(1L, 9, 18)),
                List.of(cambio), List.of(), 60);

        List<Cupo> cupos = motor.cuposDeProfesional(MARTES, ZONA, ayer(), p);

        assertEquals(MARTES.atTime(13, 0), cupos.get(0).inicio());
        assertEquals(MARTES.atTime(16, 0), cupos.get(cupos.size() - 1).inicio());
    }

    @Test
    @DisplayName("No se ofrecen cupos que ya pasaron ni dentro de la anticipación mínima")
    void respetaLaAnticipacion() {
        Instant ahora = MARTES.atTime(9, 40).atZone(ZONA).toInstant();

        var p = new DatosProfesional(1L, "Ana", List.of(horario(1L, 9, 13)),
                List.of(), List.of(), 60);

        List<Cupo> cupos = motor.cuposDeProfesional(MARTES, ZONA, ahora, p);

        // 9:40 + 30 min de anticipación = 10:10, alineado al siguiente cuarto: 10:15
        assertEquals(MARTES.atTime(10, 15), cupos.get(0).inicio());
    }

    @Test
    @DisplayName("Si el servicio no cabe en el hueco que queda, no se ofrece")
    void noOfreceLoQueNoCabe() {
        var p = new DatosProfesional(1L, "Ana", List.of(horario(1L, 9, 12)),
                List.of(), List.of(tramo(10, 0, 12, 0)), 90);

        // Solo queda de 9:00 a 10:00 y el servicio dura 90 minutos
        assertTrue(motor.cuposDeProfesional(MARTES, ZONA, ayer(), p).isEmpty());
    }

    @Test
    @DisplayName("Quien no presta el servicio no aparece en los cupos")
    void profesionalQueNoPrestaElServicio() {
        var p = new DatosProfesional(1L, "Ana", List.of(horario(1L, 9, 18)),
                List.of(), List.of(), null);

        assertTrue(motor.cuposDeProfesional(MARTES, ZONA, ayer(), p).isEmpty());
    }
}
