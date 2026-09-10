package com.agenda.reportes;

import com.agenda.agenda.*;
import com.agenda.catalogo.*;
import com.agenda.common.ContextoEmpresa;
import com.agenda.disponibilidad.DisponibilidadServicio;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Descarga de las citas para abrir en Excel.
 *
 * Se genera como CSV separado por punto y coma, que es lo que Excel en
 * español espera. Lleva la marca BOM al inicio para que las tildes y las
 * eñes se vean bien: sin eso, "Sofía" sale como "SofÃ­a".
 */
@RestController
@RequestMapping("/api/exportar")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ExportacionControlador {

    private final CitaRepositorio citas;
    private final ClienteRepositorio clientes;
    private final ServicioRepositorio servicios;
    private final ProfesionalRepositorio profesionales;
    private final DisponibilidadServicio disponibilidad;

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("hh:mm a");

    @GetMapping("/citas")
    public ResponseEntity<byte[]> citas(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        Long empresaId = ContextoEmpresa.actual();
        ZoneId zona = disponibilidad.zonaDeEmpresa(empresaId);

        Instant inicio = desde.atStartOfDay(zona).toInstant();
        Instant fin = hasta.plusDays(1).atStartOfDay(zona).toInstant();

        // Se traen los nombres de una vez, no uno por cita
        Map<Long, String> nombreServicio = new HashMap<>();
        Map<Long, Long> precioServicio = new HashMap<>();
        for (Servicio s : servicios.findByEmpresaId(empresaId)) {
            nombreServicio.put(s.getId(), s.getNombre());
            precioServicio.put(s.getId(), s.getPrecioCentavos());
        }

        Map<Long, String> nombreProfesional = new HashMap<>();
        for (Profesional p : profesionales.findByEmpresaId(empresaId)) {
            nombreProfesional.put(p.getId(), p.getNombre());
        }

        List<Cita> lista = citas.findAll().stream()
                .filter(c -> c.getEmpresaId().equals(empresaId))
                .filter(c -> !c.getInicio().isBefore(inicio) && c.getInicio().isBefore(fin))
                .sorted(Comparator.comparing(Cita::getInicio))
                .toList();

        Map<Long, Cliente> porCliente = new HashMap<>();
        clientes.findAllById(lista.stream()
                        .map(Cita::getClienteId).filter(Objects::nonNull).toList())
                .forEach(cl -> porCliente.put(cl.getId(), cl));

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');   // marca para que Excel lea las tildes

        sb.append(String.join(";",
                "Fecha", "Día", "Hora inicio", "Hora fin", "Duración (min)",
                "Clienta", "Teléfono", "Servicio", "Precio",
                "Atendida por", "Estado", "Entró por", "Notas")).append('\n');

        for (Cita c : lista) {
            LocalDateTime ini = LocalDateTime.ofInstant(c.getInicio(), zona);
            LocalDateTime fn = LocalDateTime.ofInstant(c.getFin(), zona);
            Cliente cl = c.getClienteId() == null ? null : porCliente.get(c.getClienteId());
            long minutos = Duration.between(c.getInicio(), c.getFin()).toMinutes();
            long precio = precioServicio.getOrDefault(c.getServicioId(), 0L) / 100;

            sb.append(String.join(";",
                    FECHA.format(ini),
                    campo(diaEnEspanol(ini.getDayOfWeek())),
                    HORA.format(ini),
                    HORA.format(fn),
                    String.valueOf(minutos),
                    campo(cl == null ? "" : cl.getNombre()),
                    campo(cl == null ? "" : cl.getTelefono()),
                    campo(nombreServicio.getOrDefault(c.getServicioId(), "")),
                    String.valueOf(precio),
                    campo(nombreProfesional.getOrDefault(c.getProfesionalId(), "")),
                    campo(etiquetaEstado(c.getEstado())),
                    campo(etiquetaOrigen(c.getOrigen())),
                    campo(c.getNotas() == null ? "" : c.getNotas())
            )).append('\n');
        }

        // Un resumen al final, que es lo que la gente busca primero
        long atendidas = lista.stream().filter(c -> c.getEstado() == EstadoCita.FINALIZADA).count();
        long noLlegaron = lista.stream().filter(c -> c.getEstado() == EstadoCita.NO_ASISTIO).count();
        long canceladas = lista.stream().filter(c -> c.getEstado() == EstadoCita.CANCELADA).count();
        long porWhatsapp = lista.stream().filter(c -> c.getOrigen() == OrigenCita.WHATSAPP).count();

        sb.append('\n');
        sb.append("RESUMEN DEL ").append(FECHA.format(desde))
                .append(" AL ").append(FECHA.format(hasta)).append('\n');
        sb.append("Citas agendadas;").append(lista.size()).append('\n');
        sb.append("Atendidas;").append(atendidas).append('\n');
        sb.append("No llegaron;").append(noLlegaron).append('\n');
        sb.append("Canceladas;").append(canceladas).append('\n');
        sb.append("Agendadas por WhatsApp;").append(porWhatsapp).append('\n');

        byte[] datos = sb.toString().getBytes(StandardCharsets.UTF_8);
        String archivo = "citas-" + desde + "-a-" + hasta + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(datos);
    }

    /** Escapa el punto y coma y las comillas para que no rompan las columnas. */
    private String campo(String valor) {
        if (valor == null) return "";
        String limpio = valor.replace("\"", "\"\"").replace("\n", " ");
        return limpio.contains(";") || limpio.contains("\"") ? "\"" + limpio + "\"" : limpio;
    }

    private String diaEnEspanol(DayOfWeek dia) {
        return switch (dia) {
            case MONDAY -> "Lunes";
            case TUESDAY -> "Martes";
            case WEDNESDAY -> "Miércoles";
            case THURSDAY -> "Jueves";
            case FRIDAY -> "Viernes";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }

    private String etiquetaEstado(EstadoCita estado) {
        return switch (estado) {
            case CONFIRMADA -> "Confirmada";
            case FINALIZADA -> "Atendida";
            case CANCELADA -> "Cancelada";
            case NO_ASISTIO -> "No llegó";
        };
    }

    private String etiquetaOrigen(OrigenCita origen) {
        return switch (origen) {
            case WHATSAPP -> "WhatsApp";
            case PANEL -> "El salón";
            case WEB -> "Página web";
        };
    }
}