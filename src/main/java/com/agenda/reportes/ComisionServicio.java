package com.agenda.reportes;

import com.agenda.agenda.*;
import com.agenda.catalogo.Profesional;
import com.agenda.catalogo.ProfesionalRepositorio;
import com.agenda.catalogo.Servicio;
import com.agenda.catalogo.ServicioRepositorio;
import com.agenda.common.ContextoEmpresa;
import com.agenda.disponibilidad.DisponibilidadServicio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * Las cuentas de la nómina: cuánto produjo cada profesional, cuánto se
 * le paga y cuánto le queda al salón.
 *
 * Es lo que la dueña le pasa al contador, así que los números tienen que
 * cuadrar exactos. Por eso se usa el valor que de verdad se cobró en cada
 * cita y no el precio actual del servicio: si mañana suben los precios,
 * lo del mes pasado no puede moverse.
 */
@Service
@RequiredArgsConstructor
public class ComisionServicio {

    private final CitaRepositorio citas;
    private final ClienteRepositorio clientes;
    private final ProfesionalRepositorio profesionales;
    private final ServicioRepositorio servicios;
    private final DisponibilidadServicio disponibilidad;

    /** El detalle de una profesional en el periodo. */
    public record Liquidacion(
            Long profesionalId,
            String nombre,
            int citas,
            long produccion,      // lo que facturó
            double comisionPct,
            long comision,        // lo que se le paga
            long paraElSalon      // lo que queda
    ) {}

    /** Una cita cobrada, para el detalle que revisa el contador. */
    public record Movimiento(
            LocalDate fecha,
            LocalTime hora,
            String profesional,
            String servicio,
            String cliente,
            String metodoPago,
            long valor,
            double comisionPct,
            long comision
    ) {}

    public record Reporte(
            LocalDate desde,
            LocalDate hasta,
            long produccionTotal,
            long comisionesTotal,
            long paraElSalon,
            int citasCobradas,
            int sinMetodoPago,
            List<Liquidacion> porProfesional,
            List<Object[]> porMetodoPago,   // [etiqueta, valor]
            List<Movimiento> movimientos
    ) {}

    public Reporte generar(LocalDate desde, LocalDate hasta) {
        Long empresaId = ContextoEmpresa.actual();
        ZoneId zona = disponibilidad.zonaDeEmpresa(empresaId);

        Instant inicio = desde.atStartOfDay(zona).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(zona).toInstant();

        Map<Long, Profesional> porProf = new HashMap<>();
        profesionales.findByEmpresaId(empresaId).forEach(p -> porProf.put(p.getId(), p));

        Map<Long, Servicio> porServicio = new HashMap<>();
        servicios.findByEmpresaId(empresaId).forEach(s -> porServicio.put(s.getId(), s));

        // Solo las atendidas cuentan para nómina: una cancelada no se paga
        List<Cita> atendidas = citas.findAll().stream()
                .filter(c -> c.getEmpresaId().equals(empresaId))
                .filter(c -> !c.getInicio().isBefore(inicio) && c.getInicio().isBefore(fin))
                .filter(c -> c.getEstado() == EstadoCita.FINALIZADA)
                .sorted(Comparator.comparing(Cita::getInicio))
                .toList();

        Map<Long, Cliente> porCliente = new HashMap<>();
        clientes.findAllById(atendidas.stream()
                        .map(Cita::getClienteId).filter(Objects::nonNull).toList())
                .forEach(cl -> porCliente.put(cl.getId(), cl));

        Map<Long, long[]> acumulado = new HashMap<>();   // profId -> [citas, produccion]
        Map<String, Long> porMetodo = new LinkedHashMap<>();
        porMetodo.put("Efectivo", 0L);
        porMetodo.put("Transferencia", 0L);
        porMetodo.put("Tarjeta", 0L);
        porMetodo.put("Otro", 0L);
        porMetodo.put("Sin marcar", 0L);

        List<Movimiento> movimientos = new ArrayList<>();
        int sinMetodo = 0;

        for (Cita c : atendidas) {
            long valor = valorDe(c, porServicio);
            Profesional p = porProf.get(c.getProfesionalId());
            double pct = p == null ? 50.0 : p.getComisionPct().doubleValue();

            long[] acu = acumulado.computeIfAbsent(c.getProfesionalId(), k -> new long[2]);
            acu[0]++;
            acu[1] += valor;

            String metodo = c.getMetodoPago() == null ? "Sin marcar" : c.getMetodoPago().etiqueta();
            if (c.getMetodoPago() == null) sinMetodo++;
            porMetodo.merge(metodo, valor, Long::sum);

            LocalDateTime cuando = LocalDateTime.ofInstant(c.getInicio(), zona);
            Cliente cl = c.getClienteId() == null ? null : porCliente.get(c.getClienteId());

            movimientos.add(new Movimiento(
                    cuando.toLocalDate(), cuando.toLocalTime(),
                    p == null ? "—" : p.getNombre(),
                    porServicio.containsKey(c.getServicioId())
                            ? porServicio.get(c.getServicioId()).getNombre() : "—",
                    cl == null ? "" : cl.getNombre(),
                    metodo, valor, pct, calcular(valor, pct)));
        }

        List<Liquidacion> liquidaciones = new ArrayList<>();
        long produccionTotal = 0, comisionesTotal = 0;

        for (Map.Entry<Long, long[]> e : acumulado.entrySet()) {
            Profesional p = porProf.get(e.getKey());
            if (p == null) continue;

            double pct = p.getComisionPct().doubleValue();
            long produccion = e.getValue()[1];
            long comision = calcular(produccion, pct);

            liquidaciones.add(new Liquidacion(
                    p.getId(), p.getNombre(), (int) e.getValue()[0],
                    produccion, pct, comision, produccion - comision));

            produccionTotal += produccion;
            comisionesTotal += comision;
        }
        liquidaciones.sort(Comparator.comparingLong(Liquidacion::produccion).reversed());

        // Se quitan los métodos que no se usaron, para no mostrar ceros
        List<Object[]> metodos = porMetodo.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> new Object[]{ e.getKey(), e.getValue() })
                .toList();

        return new Reporte(desde, hasta,
                produccionTotal, comisionesTotal, produccionTotal - comisionesTotal,
                atendidas.size(), sinMetodo,
                liquidaciones, metodos, movimientos);
    }

    /**
     * Lo que se cobró de verdad. Si la cita no lo tiene guardado —porque
     * es anterior a esta función— se cae al precio del servicio.
     */
    private long valorDe(Cita c, Map<Long, Servicio> porServicio) {
        if (c.getValorCobradoCentavos() != null && c.getValorCobradoCentavos() > 0) {
            return c.getValorCobradoCentavos();
        }
        Servicio s = porServicio.get(c.getServicioId());
        return s == null || s.getPrecioCentavos() == null ? 0L : s.getPrecioCentavos();
    }

    /** Redondeo al peso, hacia arriba desde medio: como se paga en la práctica. */
    private long calcular(long valorCentavos, double pct) {
        return BigDecimal.valueOf(valorCentavos)
                .multiply(BigDecimal.valueOf(pct))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                .longValue();
    }
}