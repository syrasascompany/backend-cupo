package com.agenda.notificaciones;

import com.agenda.agenda.*;
import com.agenda.catalogo.Profesional;
import com.agenda.catalogo.Servicio;
import com.agenda.empresa.Empresa;
import com.agenda.empresa.EmpresaRepositorio;
import com.agenda.espera.*;
import com.agenda.whatsapp.WhatsappCliente;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.agenda.catalogo.ServicioRepositorio;
import com.agenda.catalogo.ProfesionalRepositorio;

/**
 * Recordatorios y aviso de cupos liberados.
 *
 * Ambos salen FUERA de la ventana de 24 horas, así que van por plantilla
 * aprobada por Meta. Hay que crearlas antes en el administrador de
 * WhatsApp; los nombres están en la configuración.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RecordatorioTarea {

    private final CitaRepositorio citas;
    private final ClienteRepositorio clientes;
    private final ServicioRepositorio servicios;
    private final ProfesionalRepositorio profesionales;
    private final EmpresaRepositorio empresas;
    private final ListaEsperaRepositorio listaEspera;
    private final NotificacionRepositorio notificaciones;
    private final WhatsappCliente wa;

    private static final DateTimeFormatter DIA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", new Locale("es", "CO"));
    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("h:mm a", new Locale("es", "CO"));

    /**
     * Corre cada 10 minutos y busca citas que empiecen dentro de ~24 horas
     * o de ~2 horas. La tabla de notificaciones impide repetir el aviso.
     */
    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void enviarRecordatorios() {
        revisarVentana(Duration.ofHours(24), "RECORDATORIO_24H");
        revisarVentana(Duration.ofHours(2),  "RECORDATORIO_2H");
    }

    private void revisarVentana(Duration anticipacion, String tipo) {
        Instant ahora = Instant.now();
        Instant desde = ahora.plus(anticipacion);
        Instant hasta = desde.plus(Duration.ofMinutes(10));

        for (Empresa empresa : empresas.findAll()) {
            if (!Boolean.TRUE.equals(empresa.getActiva())) continue;
            if (empresa.getWaPhoneNumberId() == null) continue;

            for (Cita cita : citas.vivasEnRango(empresa.getId(), desde, hasta)) {
                if (cita.getEstado() != EstadoCita.CONFIRMADA) continue;
                if (cita.getInicio().isBefore(desde) || cita.getInicio().isAfter(hasta)) continue;
                if (notificaciones.existsByCitaIdAndTipo(cita.getId(), tipo)) continue;

                String telefono = telefonoDe(cita);
                if (telefono == null) continue;

                ZoneId zona = ZoneId.of(empresa.getZonaHoraria());
                LocalDateTime inicio = LocalDateTime.ofInstant(cita.getInicio(), zona);

                try {
                    wa.plantilla(empresa.getWaPhoneNumberId(), empresa.getWaToken(), telefono,
                            tipo.equals("RECORDATORIO_24H")
                                    ? "recordatorio_cita_24h" : "recordatorio_cita_2h",
                            "es",
                            List.of(nombreDe(cita),
                                    nombreServicio(cita.getServicioId()),
                                    DIA.format(inicio),
                                    HORA.format(inicio),
                                    nombreProfesional(cita.getProfesionalId())));

                    registrar(empresa.getId(), cita.getId(), tipo, telefono, null);

                    // La plantilla no lleva botones, así que se manda una
                    // segunda línea con la instrucción. Cae dentro de la
                    // ventana de 24 horas si la clienta responde.
                    if (tipo.equals("RECORDATORIO_24H")) {
                        wa.texto(empresa.getWaPhoneNumberId(), empresa.getWaToken(), telefono,
                                "Si necesita cambiarla o cancelarla, respóndame «cambiar» "
                                        + "y le muestro sus citas.");
                    }

                } catch (Exception e) {
                    log.error("Falló el recordatorio de la cita {}: {}", cita.getId(), e.getMessage());
                }
            }
        }
    }

    /**
     * Cuando se cancela una cita, se le ofrece el cupo a la lista de espera.
     * Esta es la función que convierte el sistema de gasto en ingreso:
     * un cupo de dos horas recuperado paga varios meses de suscripción.
     */
    @Transactional
    public void avisarCupoLiberado(Cita cancelada) {
        Empresa empresa = empresas.findById(cancelada.getEmpresaId()).orElse(null);
        if (empresa == null || empresa.getWaPhoneNumberId() == null) return;

        ZoneId zona = ZoneId.of(empresa.getZonaHoraria());
        LocalDateTime inicio = LocalDateTime.ofInstant(cancelada.getInicio(), zona);

        List<ListaEspera> candidatos = listaEspera.candidatos(
                empresa.getId(), cancelada.getServicioId(),
                cancelada.getProfesionalId(), inicio.toLocalDate());

        // Se avisa a las tres primeras: si se avisa a todas, se genera
        // una carrera y quedan dos clientas molestas.
        int avisados = 0;
        for (ListaEspera espera : candidatos) {
            if (avisados >= 3) break;
            try {
                wa.plantilla(empresa.getWaPhoneNumberId(), empresa.getWaToken(),
                        espera.getTelefono(), "cupo_disponible", "es",
                        List.of(espera.getNombre() == null ? "Hola" : espera.getNombre(),
                                nombreServicio(cancelada.getServicioId()),
                                DIA.format(inicio),
                                HORA.format(inicio)));

                espera.setEstado(EstadoEspera.AVISADO);
                espera.setAvisadoEn(Instant.now());
                registrar(empresa.getId(), cancelada.getId(), "CUPO_LIBRE",
                        espera.getTelefono(), null);
                avisados++;

            } catch (Exception e) {
                log.error("Falló el aviso de cupo a {}: {}", espera.getTelefono(), e.getMessage());
            }
        }
        if (avisados > 0) {
            log.info("Cupo liberado de la cita {}: se avisó a {} personas en espera",
                    cancelada.getId(), avisados);
        }
    }

    /** Limpia las esperas vencidas una vez al día. */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void vencerEsperas() {
        LocalDate hoy = LocalDate.now();
        for (Empresa e : empresas.findAll()) {
            listaEspera.findByEmpresaIdAndEstadoOrderByCreadoEnAsc(e.getId(), EstadoEspera.ESPERANDO)
                    .stream()
                    .filter(x -> x.getHasta().isBefore(hoy))
                    .forEach(x -> x.setEstado(EstadoEspera.VENCIDO));
        }
    }

    private void registrar(Long empresaId, Long citaId, String tipo, String destino, String detalle) {
        Notificacion n = new Notificacion();
        n.setEmpresaId(empresaId);
        n.setCitaId(citaId);
        n.setTipo(tipo);
        n.setDestino(destino);
        n.setDetalle(detalle);
        notificaciones.save(n);
    }

    private String telefonoDe(Cita cita) {
        if (cita.getClienteId() == null) return null;
        return clientes.findById(cita.getClienteId()).map(Cliente::getTelefono).orElse(null);
    }

    private String nombreDe(Cita cita) {
        if (cita.getClienteId() == null) return "Hola";
        return clientes.findById(cita.getClienteId()).map(Cliente::getNombre).orElse("Hola");
    }

    private String nombreServicio(Long id) {
        return servicios.findById(id).map(Servicio::getNombre).orElse("su servicio");
    }

    private String nombreProfesional(Long id) {
        return profesionales.findById(id).map(Profesional::getNombre).orElse("");
    }
}