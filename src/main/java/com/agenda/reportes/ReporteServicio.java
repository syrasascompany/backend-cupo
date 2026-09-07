package com.agenda.reportes;

import com.agenda.agenda.*;
import com.agenda.catalogo.Profesional;
import com.agenda.catalogo.ProfesionalRepositorio;
import com.agenda.common.ContextoEmpresa;
import com.agenda.disponibilidad.DisponibilidadServicio;
import com.agenda.espera.EstadoEspera;
import com.agenda.espera.ListaEsperaRepositorio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;

/**
 * Los números con los que se justifica la mensualidad.
 *
 * Todo sale de datos que ya se estaban guardando: no hubo que registrar
 * nada nuevo, solo sumarlo.
 */
@Service
@RequiredArgsConstructor
public class ReporteServicio {

    private final CitaRepositorio citas;
    private final ClienteRepositorio clientes;
    private final ProfesionalRepositorio profesionales;
    private final HorarioRepositorio horarios;
    private final ExcepcionRepositorio excepciones;
    private final ListaEsperaRepositorio listaEspera;
    private final DisponibilidadServicio disponibilidad;

    public record OcupacionProfesional(
            Long profesionalId, String nombre,
            long minutosDisponibles, long minutosOcupados,
            int porcentaje, int citas) {}

    public record ClienteFallon(String nombre, String telefono, int faltas) {}

    public record Reporte(
            LocalDate desde, LocalDate hasta,
            int citasTotales,
            int finalizadas,
            int canceladas,
            int noAsistio,
            int porWhatsapp,
            int porPanel,
            int porcentajeNoShow,
            int ocupacionGeneral,
            long minutosVendidos,
            int cuposRecuperados,
            List<OcupacionProfesional> porProfesional,
            List<ClienteFallon> masFallan) {}

    public Reporte generar(LocalDate desde, LocalDate hasta) {
        Long empresaId = ContextoEmpresa.actual();
        ZoneId zona = disponibilidad.zonaDeEmpresa(empresaId);

        Instant inicio = desde.atStartOfDay(zona).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(zona).toInstant();

        List<Profesional> activos = profesionales.findByEmpresaIdAndActivoTrue(empresaId);
        List<Long> ids = activos.stream().map(Profesional::getId).toList();

        // Todas las citas del rango, incluidas canceladas y ausencias
        List<Cita> todas = citas.findAll().stream()
                .filter(c -> c.getEmpresaId().equals(empresaId))
                .filter(c -> !c.getInicio().isBefore(inicio) && c.getInicio().isBefore(fin))
                .toList();

        int finalizadas = (int) todas.stream().filter(c -> c.getEstado() == EstadoCita.FINALIZADA).count();
        int canceladas  = (int) todas.stream().filter(c -> c.getEstado() == EstadoCita.CANCELADA).count();
        int noAsistio   = (int) todas.stream().filter(c -> c.getEstado() == EstadoCita.NO_ASISTIO).count();
        int porWhatsapp = (int) todas.stream().filter(c -> c.getOrigen() == OrigenCita.WHATSAPP).count();
        int porPanel    = (int) todas.stream().filter(c -> c.getOrigen() == OrigenCita.PANEL).count();

        // El no-show se mide contra las que de verdad iban a atenderse
        int atendibles = finalizadas + noAsistio;
        int porcentajeNoShow = atendibles == 0 ? 0
                : (int) Math.round(noAsistio * 100.0 / atendibles);

        // Ocupación por persona
        Map<Long, Long> disponiblePorProf = new HashMap<>();
        for (Long id : ids) disponiblePorProf.put(id, minutosDisponibles(id, desde, hasta));

        List<OcupacionProfesional> porProfesional = new ArrayList<>();
        long vendidosTotal = 0, disponibleTotal = 0;

        for (Profesional p : activos) {
            List<Cita> suyas = todas.stream()
                    .filter(c -> c.getProfesionalId().equals(p.getId()))
                    .filter(Cita::estaViva)
                    .toList();

            long ocupados = suyas.stream()
                    .mapToLong(c -> Duration.between(c.getInicio(), c.getFin()).toMinutes())
                    .sum();

            long disponibles = disponiblePorProf.getOrDefault(p.getId(), 0L);
            int pct = disponibles == 0 ? 0 : (int) Math.round(ocupados * 100.0 / disponibles);

            porProfesional.add(new OcupacionProfesional(
                    p.getId(), p.getNombre(), disponibles, ocupados, pct, suyas.size()));

            vendidosTotal += ocupados;
            disponibleTotal += disponibles;
        }
        porProfesional.sort(Comparator.comparingInt(OcupacionProfesional::porcentaje).reversed());

        int ocupacionGeneral = disponibleTotal == 0 ? 0
                : (int) Math.round(vendidosTotal * 100.0 / disponibleTotal);

        // Quiénes faltan más: sirve para decidir a quién pedirle abono
        Map<Long, Integer> faltasPorCliente = new HashMap<>();
        for (Cita c : todas) {
            if (c.getEstado() == EstadoCita.NO_ASISTIO && c.getClienteId() != null) {
                faltasPorCliente.merge(c.getClienteId(), 1, Integer::sum);
            }
        }
        List<ClienteFallon> masFallan = faltasPorCliente.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> clientes.findById(e.getKey())
                        .map(cl -> new ClienteFallon(cl.getNombre(), cl.getTelefono(), e.getValue()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();

        // Cupos que se recuperaron gracias a la lista de espera
        int cuposRecuperados = (int) listaEspera
                .findByEmpresaIdAndEstadoOrderByCreadoEnAsc(empresaId, EstadoEspera.TOMO_CUPO)
                .stream()
                .filter(e -> !e.getCreadoEn().isBefore(inicio) && e.getCreadoEn().isBefore(fin))
                .count();

        return new Reporte(desde, hasta,
                todas.size(), finalizadas, canceladas, noAsistio,
                porWhatsapp, porPanel, porcentajeNoShow,
                ocupacionGeneral, vendidosTotal, cuposRecuperados,
                porProfesional, masFallan);
    }

    /**
     * Minutos que la profesional tuvo disponibles en el rango: su horario
     * normal, menos ausencias y permisos.
     */
    private long minutosDisponibles(Long profesionalId, LocalDate desde, LocalDate hasta) {
        List<HorarioBase> base = horarios.findByProfesionalId(profesionalId);
        List<ExcepcionHorario> exc = excepciones
                .findByProfesionalIdAndFechaBetween(profesionalId, desde, hasta);

        long total = 0;
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            final LocalDate d = dia;

            List<ExcepcionHorario> delDia = exc.stream()
                    .filter(e -> e.getFecha().equals(d)).toList();

            if (delDia.stream().anyMatch(e -> e.getTipo() == TipoExcepcion.AUSENCIA)) continue;

            List<ExcepcionHorario> cambios = delDia.stream()
                    .filter(e -> e.getTipo() == TipoExcepcion.CAMBIO_HORARIO)
                    .filter(e -> e.getHoraInicio() != null && e.getHoraFin() != null)
                    .toList();

            long minutosDia;
            if (!cambios.isEmpty()) {
                minutosDia = cambios.stream()
                        .mapToLong(e -> Duration.between(e.getHoraInicio(), e.getHoraFin()).toMinutes())
                        .sum();
            } else {
                short diaSemana = (short) d.getDayOfWeek().getValue();
                minutosDia = base.stream()
                        .filter(h -> h.getDiaSemana() == diaSemana)
                        .mapToLong(h -> Duration.between(h.getHoraInicio(), h.getHoraFin()).toMinutes())
                        .sum();
            }

            // Los permisos de unas horas se descuentan
            long bloqueado = delDia.stream()
                    .filter(e -> e.getTipo() == TipoExcepcion.BLOQUEO)
                    .filter(e -> e.getHoraInicio() != null && e.getHoraFin() != null)
                    .mapToLong(e -> Duration.between(e.getHoraInicio(), e.getHoraFin()).toMinutes())
                    .sum();

            total += Math.max(0, minutosDia - bloqueado);
        }
        return total;
    }
}
