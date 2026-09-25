package com.agenda.whatsapp;

import com.agenda.agenda.*;
import com.agenda.catalogo.*;
import com.agenda.common.ContextoEmpresa;
import com.agenda.disponibilidad.DisponibilidadServicio;
import com.agenda.disponibilidad.MotorDisponibilidad.Cupo;
import com.agenda.empresa.Empresa;
import com.agenda.espera.*;
import com.agenda.whatsapp.WhatsappCliente.Opcion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * El flujo del bot, paso a paso.
 *
 * Todo va con listas y botones, nunca con frases abiertas: cada respuesta
 * del bot cuesta dinero desde octubre de 2026, y además una lista se
 * equivoca mucho menos que intentar adivinar lo que escribió la clienta.
 *
 * El tono es el de la recepcionista del salón: cálida, pero al grano,
 * porque sabe que la clienta está ocupada. Si algo se sale del guion, el
 * bot se hace a un lado y avisa que contesta una persona. Nunca inventa
 * un cupo.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BotServicio {

    private final ConversacionRepositorio conversaciones;
    private final ServicioRepositorio servicios;
    private final ProfesionalRepositorio profesionales;
    private final DisponibilidadServicio disponibilidad;
    private final CitaServicio citas;
    private final ListaEsperaRepositorio listaEspera;
    private final CitaRepositorio citasRepo;
    private final ClienteRepositorio clientesRepo;
    private final WhatsappCliente wa;

    private static final DateTimeFormatter DIA =
            DateTimeFormatter.ofPattern("EEE d 'de' MMM", new Locale("es", "CO"));
    private static final DateTimeFormatter HORA =
            DateTimeFormatter.ofPattern("h:mm a", new Locale("es", "CO"));

    /** Lo que llega del webhook, ya limpio. */
    public record Entrante(String telefono, String nombrePerfil,
                           String texto, String idSeleccion) {}

    /** Cuánto se queda callado el bot después de que responde una persona. */
    private static final Duration PAUSA = Duration.ofHours(12);

    /**
     * Alguien del salón le escribió a esta clienta desde el celular.
     * Llega por el evento smb_message_echoes de Coexistence.
     */
    @Transactional
    public void pausarPorHumano(Empresa empresa, String telefono) {
        Conversacion c = conversaciones
                .findByEmpresaIdAndTelefono(empresa.getId(), telefono)
                .orElseGet(() -> {
                    Conversacion nueva = new Conversacion();
                    nueva.setEmpresaId(empresa.getId());
                    nueva.setTelefono(telefono);
                    return nueva;
                });

        c.setPausadoHasta(Instant.now().plus(PAUSA));
        c.setPaso(PasoBot.ESPERANDO_HUMANO);
        conversaciones.save(c);

        log.info("Bot pausado con {} por 12 horas: contestó una persona", telefono);
    }

    @Transactional
    public void procesar(Empresa empresa, Entrante entrada) {
        ContextoEmpresa.fijar(empresa.getId());
        try {
            Conversacion c = conversaciones
                    .findByEmpresaIdAndTelefono(empresa.getId(), entrada.telefono())
                    .orElseGet(() -> {
                        Conversacion nueva = new Conversacion();
                        nueva.setEmpresaId(empresa.getId());
                        nueva.setTelefono(entrada.telefono());
                        return nueva;
                    });

            c.setUltimoMensaje(Instant.now());

            // El nombre del perfil de WhatsApp casi nunca trae apellido, y a
            // veces es un apodo. Solo se toma si parece un nombre completo.
            if (c.getNombreCliente() == null && esNombreCompleto(entrada.nombrePerfil())) {
                c.setNombreCliente(limpiarNombre(entrada.nombrePerfil()));
            }

            String seleccion = entrada.idSeleccion();
            String texto = entrada.texto() == null ? "" : entrada.texto().trim().toLowerCase();

            // Si una persona del salón está atendiendo, el bot se queda callado.
            // Solo vuelve si la clienta pide expresamente agendar.
            boolean quiereBot = seleccion != null
                    || texto.equals("menu") || texto.equals("menú")
                    || texto.equals("inicio") || texto.contains("cita")
                    || texto.contains("agendar");

            if (c.estaPausada() && !quiereBot) {
                log.debug("Bot en pausa para {}: contesta una persona", entrada.telefono());
                conversaciones.save(c);
                return;
            }
            if (c.estaPausada() && quiereBot) {
                c.setPausadoHasta(null);   // la clienta pidió el bot de vuelta
            }

            // Salidas de emergencia, válidas en cualquier paso
            if (seleccion == null && (texto.contains("asesor") || texto.contains("persona")
                    || texto.contains("hablar con"))) {
                c.setPaso(PasoBot.ESPERANDO_HUMANO);
                conversaciones.save(c);
                responder(empresa, entrada.telefono(),
                        "Claro que sí 🙌 Ya le responde alguien del salón.");
                return;
            }
            // Cambiar o cancelar tiene prioridad sobre agendar
            if (seleccion == null && (texto.contains("cancelar")
                    || texto.contains("cambiar") || texto.contains("mover")
                    || texto.contains("reprogramar") || texto.contains("correr"))) {
                mostrarMisCitas(empresa, c);
                conversaciones.save(c);
                return;
            }
            if (seleccion == null && (texto.equals("menu") || texto.equals("menú")
                    || texto.equals("inicio") || texto.contains("cita"))) {
                c.reiniciar();
            }

            switch (c.getPaso()) {
                case INICIO, ESPERANDO_HUMANO -> mostrarServicios(empresa, c);
                case ELIGIENDO_SERVICIO -> recibirServicio(empresa, c, seleccion);
                case ELIGIENDO_FECHA -> recibirFecha(empresa, c, seleccion);
                case ELIGIENDO_FRANJA -> recibirFranja(empresa, c, seleccion);
                case ELIGIENDO_CUPO -> recibirCupo(empresa, c, seleccion);
                case PIDIENDO_NOMBRE -> recibirNombre(empresa, c, entrada.texto());
                case ELIGIENDO_CITA -> recibirCita(empresa, c, seleccion);
                case QUE_HACER_CON_CITA -> recibirQueHacer(empresa, c, seleccion);
                case REPROGRAMANDO_FECHA -> recibirFechaNueva(empresa, c, seleccion);
                case REPROGRAMANDO_CUPO -> recibirCupoNuevo(empresa, c, seleccion);
                case ELIGIENDO_ESPERA -> recibirEspera(empresa, c, seleccion);
            }
            conversaciones.save(c);

        } finally {
            ContextoEmpresa.limpiar();
        }
    }

    // ---------------- Paso 1: qué servicio ----------------

    /**
     * El saludo. Si la clienta ya tiene citas, lo primero que ve es eso:
     * si no, escribe "hola" y termina sacando una cita repetida sin querer.
     */
    private void mostrarServicios(Empresa empresa, Conversacion c) {
        List<Cita> suyas = misCitas(empresa, c.getTelefono());

        if (!suyas.isEmpty()) {
            saludarConCitas(empresa, c, suyas);
            return;
        }
        mostrarSoloServicios(empresa, c, """
                ¡Hola! 💅 Bienvenida a %s.

                Cuénteme qué se quiere hacer y le muestro los horarios que tenemos libres."""
                .formatted(empresa.getNombre()));
    }

    /** Para quien ya tiene cita: se le dice y se le dan las tres salidas. */
    private void saludarConCitas(Empresa empresa, Conversacion c, List<Cita> suyas) {
        ZoneId zona = ZoneId.of(empresa.getZonaHoraria());
        Cita proxima = suyas.get(0);
        LocalDateTime inicio = LocalDateTime.ofInstant(proxima.getInicio(), zona);

        String encabezado = """
                ¡Hola de nuevo! 💅

                La tenemos anotada para el %s a las %s, para %s.

                ¿En qué le ayudo?"""
                .formatted(DIA.format(inicio), HORA.format(inicio),
                        nombreServicio(proxima.getServicioId()));

        wa.botones(empresa.getWaPhoneNumberId(), empresa.getWaToken(), c.getTelefono(),
                encabezado,
                List.of(new Opcion("otra_cita", "Quiero otra cita", null),
                        new Opcion("ver_mis_citas", "Mover o cancelar", null),
                        new Opcion("hablar", "Hablar con el salón", null)));

        c.setPaso(PasoBot.ELIGIENDO_SERVICIO);
    }

    private void mostrarSoloServicios(Empresa empresa, Conversacion c, String encabezado) {
        List<Servicio> lista = servicios.findByEmpresaIdAndActivoTrue(empresa.getId());
        if (lista.isEmpty()) {
            // La clienta no tiene por qué enterarse de que falta configurar algo
            responder(empresa, c.getTelefono(),
                    "Deme un segundo, ya le responde alguien del salón 🙌");
            c.setPaso(PasoBot.ESPERANDO_HUMANO);
            return;
        }

        List<Opcion> opciones = lista.stream()
                .map(s -> new Opcion("srv_" + s.getId(), s.getNombre(), precio(s)))
                .toList();

        wa.lista(empresa.getWaPhoneNumberId(), empresa.getWaToken(), c.getTelefono(),
                encabezado, "Ver servicios", "Servicios", opciones);

        c.setPaso(PasoBot.ELIGIENDO_SERVICIO);
    }

    /** Las citas próximas de ese teléfono, de la más cercana en adelante. */
    private List<Cita> misCitas(Empresa empresa, String telefono) {
        Instant desde = Instant.now();
        Instant hasta = desde.plus(Duration.ofDays(60));

        return citasRepo.vivasEnRango(empresa.getId(), desde, hasta).stream()
                .filter(x -> x.getEstado() == EstadoCita.CONFIRMADA)
                .filter(x -> esDe(telefono, x))
                .sorted(Comparator.comparing(Cita::getInicio))
                .limit(9)
                .toList();
    }

    private String precio(Servicio s) {
        if (s.getPrecioCentavos() == null || s.getPrecioCentavos() <= 0) return null;
        return "$" + String.format("%,d", s.getPrecioCentavos() / 100).replace(',', '.');
    }

    // ---------------- Paso 2: qué día ----------------

    private void recibirServicio(Empresa empresa, Conversacion c, String seleccion) {
        // Salidas del saludo de quien ya tiene cita
        if ("ver_mis_citas".equals(seleccion)) { mostrarMisCitas(empresa, c); return; }
        if ("hablar".equals(seleccion)) {
            c.setPaso(PasoBot.ESPERANDO_HUMANO);
            c.setPausadoHasta(Instant.now().plus(Duration.ofHours(4)));
            responder(empresa, c.getTelefono(),
                    "Claro que sí 🙌 Ya le responde alguien del salón.");
            return;
        }
        if ("otra_cita".equals(seleccion)) {
            mostrarSoloServicios(empresa, c, "¿Qué se quiere hacer esta vez?");
            return;
        }

        if (seleccion == null || !seleccion.startsWith("srv_")) {
            mostrarServicios(empresa, c);
            return;
        }
        c.setServicioId(Long.valueOf(seleccion.substring(4)));
        mostrarFechas(empresa, c);
    }

    private void mostrarFechas(Empresa empresa, Conversacion c) {
        ZoneId zona = ZoneId.of(empresa.getZonaHoraria());
        LocalDate hoy = LocalDate.now(zona);

        List<Opcion> opciones = new ArrayList<>();
        for (int i = 0; i < 14 && opciones.size() < 8; i++) {
            LocalDate fecha = hoy.plusDays(i);
            // Solo se ofrecen días que de verdad tengan cupos
            if (!disponibilidad.cupos(c.getServicioId(), fecha, null).isEmpty()) {
                String etiqueta = i == 0 ? "Hoy" : i == 1 ? "Mañana" : DIA.format(fecha);
                opciones.add(new Opcion("fec_" + fecha, etiqueta, null));
            }
        }

        if (opciones.isEmpty()) {
            ofrecerListaEspera(empresa, c);
            return;
        }

        wa.lista(empresa.getWaPhoneNumberId(), empresa.getWaToken(), c.getTelefono(),
                "¿Qué día le sirve?", "Ver días", "Días con campo", opciones);
        c.setPaso(PasoBot.ELIGIENDO_FECHA);
    }

    // ---------------- Paso 3: qué hora ----------------

    private void recibirFecha(Empresa empresa, Conversacion c, String seleccion) {
        if (seleccion == null || !seleccion.startsWith("fec_")) {
            mostrarFechas(empresa, c);
            return;
        }
        c.setFecha(LocalDate.parse(seleccion.substring(4)));
        mostrarFranjas(empresa, c);
    }

    /**
     * WhatsApp solo deja 10 filas por lista, y con ocho manicuristas eso se
     * llena en la primera hora. Se pregunta primero la franja: menos opciones,
     * más fáciles de leer, y queda espacio para las demás salidas.
     *
     * No se dice cuántos cupos quedan libres: "3 libres" le está diciendo a
     * la clienta que el salón está vacío, y eso no ayuda a nadie.
     */
    private void mostrarFranjas(Empresa empresa, Conversacion c) {
        List<Cupo> cupos = disponibilidad.cupos(c.getServicioId(), c.getFecha(), null);
        if (cupos.isEmpty()) {
            responder(empresa, c.getTelefono(), "Se nos llenó ese día 😅 Miremos otro.");
            mostrarFechas(empresa, c);
            return;
        }

        boolean manana = cupos.stream().anyMatch(x -> franjaDe(x) == 1);
        boolean tarde  = cupos.stream().anyMatch(x -> franjaDe(x) == 2);
        boolean noche  = cupos.stream().anyMatch(x -> franjaDe(x) == 3);

        List<Opcion> opciones = new ArrayList<>();
        if (manana) opciones.add(new Opcion("fra_1", "En la mañana", "Hasta el mediodía"));
        if (tarde)  opciones.add(new Opcion("fra_2", "En la tarde", "Después de almuerzo"));
        if (noche)  opciones.add(new Opcion("fra_3", "Al final del día", "Después de las 5"));

        opciones.add(new Opcion("otro_dia", "Mejor otro día", null));
        opciones.add(new Opcion("avisenme", "Ninguna hora me sirve",
                "Avísenme si se desocupa"));

        wa.lista(empresa.getWaPhoneNumberId(), empresa.getWaToken(), c.getTelefono(),
                "Para el " + DIA.format(c.getFecha()) + " sí tenemos campo 🙌\n\n"
                        + "¿A qué hora le sirve mejor?",
                "Ver horarios", "Franjas", opciones);
        c.setPaso(PasoBot.ELIGIENDO_FRANJA);
    }

    /** 1 = mañana, 2 = tarde, 3 = noche. */
    private int franjaDe(Cupo cupo) {
        int hora = cupo.inicio().getHour();
        if (hora < 12) return 1;
        return hora < 17 ? 2 : 3;
    }

    private void recibirFranja(Empresa empresa, Conversacion c, String seleccion) {
        if ("otro_dia".equals(seleccion)) { mostrarFechas(empresa, c); return; }
        if ("avisenme".equals(seleccion)) { preguntarRangoEspera(empresa, c); return; }

        if (seleccion == null || !seleccion.startsWith("fra_")) {
            mostrarFranjas(empresa, c);
            return;
        }
        int franja = Integer.parseInt(seleccion.substring(4));

        List<Cupo> cupos = disponibilidad.cupos(c.getServicioId(), c.getFecha(), null).stream()
                .filter(x -> franjaDe(x) == franja)
                .toList();

        if (cupos.isEmpty()) { mostrarFranjas(empresa, c); return; }

        // Un cupo por hora: si tres chicas están libres a las 9, se ofrece una.
        // La clienta no está escogiendo persona, está escogiendo hora.
        List<Opcion> opciones = new ArrayList<>();
        Set<LocalTime> horasVistas = new HashSet<>();
        for (Cupo cupo : cupos) {
            if (!horasVistas.add(cupo.inicio().toLocalTime())) continue;
            opciones.add(new Opcion(
                    "cup_" + cupo.profesionalId() + "_" + cupo.inicio(),
                    HORA.format(cupo.inicio()),
                    "La atiende " + cupo.profesionalNombre()));
            if (opciones.size() == 8) break;
        }
        opciones.add(new Opcion("otra_franja", "Ver otra franja", null));
        opciones.add(new Opcion("avisenme", "Ninguna hora me sirve",
                "Avísenme si se desocupa"));

        wa.lista(empresa.getWaPhoneNumberId(), empresa.getWaToken(), c.getTelefono(),
                "Estas son las horas que tenemos 👇", "Ver horarios", "Horarios", opciones);
        c.setPaso(PasoBot.ELIGIENDO_CUPO);
    }

    // ---------------- Paso 4: confirmar ----------------

    private void recibirCupo(Empresa empresa, Conversacion c, String seleccion) {
        if ("otro_dia".equals(seleccion)) { mostrarFechas(empresa, c); return; }
        if ("otra_franja".equals(seleccion)) { mostrarFranjas(empresa, c); return; }
        if ("avisenme".equals(seleccion)) { preguntarRangoEspera(empresa, c); return; }

        if (seleccion == null || !seleccion.startsWith("cup_")) {
            mostrarFranjas(empresa, c);
            return;
        }

        String[] partes = seleccion.split("_", 3);
        Long profesionalId = Long.valueOf(partes[1]);
        LocalDateTime inicio = LocalDateTime.parse(partes[2]);

        ZoneId zona = ZoneId.of(empresa.getZonaHoraria());
        c.setProfesionalId(profesionalId);
        c.setInicioElegido(inicio.atZone(zona).toInstant());

        if (!esNombreCompleto(c.getNombreCliente())) {
            responder(empresa, c.getTelefono(),
                    "¡Listo! Ya casi 🙌\n\n¿A nombre de quién la anoto? "
                            + "Nombre y apellido, por favor.");
            c.setPaso(PasoBot.PIDIENDO_NOMBRE);
            return;
        }
        confirmar(empresa, c, inicio);
    }

    private void recibirNombre(Empresa empresa, Conversacion c, String texto) {
        if (texto == null || texto.isBlank()) {
            responder(empresa, c.getTelefono(),
                    "¿A nombre de quién la anoto? Nombre y apellido, por favor.");
            return;
        }
        if (!esNombreCompleto(texto)) {
            // Dar la razón hace que no se sienta un trámite
            responder(empresa, c.getTelefono(),
                    "¿Y el apellido? Es que a veces se nos repiten los nombres 😊");
            return;
        }
        c.setNombreCliente(limpiarNombre(texto));
        ZoneId zona = ZoneId.of(empresa.getZonaHoraria());
        confirmar(empresa, c, LocalDateTime.ofInstant(c.getInicioElegido(), zona));
    }

    private void confirmar(Empresa empresa, Conversacion c, LocalDateTime inicio) {
        try {
            Cita cita = citas.crear(c.getServicioId(), c.getProfesionalId(), inicio,
                    c.getNombreCliente(), c.getTelefono(), OrigenCita.WHATSAPP, null);

            String servicioNombre = servicios.findById(c.getServicioId())
                    .map(Servicio::getNombre).orElse("su servicio");
            String profNombre = profesionales.findById(c.getProfesionalId())
                    .map(Profesional::getNombre).orElse("");

            responder(empresa, c.getTelefono(), """
                    ¡Lista su cita! ✨

                    📅 %s a las %s
                    💅 %s
                    👤 La atiende %s

                    Le escribimos el día anterior para recordarle.
                    Si le cambia algo, escríbame *cambiar* y lo movemos.

                    ¡La esperamos! 💕"""
                    .formatted(DIA.format(inicio), HORA.format(inicio),
                            servicioNombre, profNombre));

            log.info("Cita {} creada por WhatsApp para {}", cita.getId(), c.getTelefono());
            c.reiniciar();

        } catch (Exception e) {
            // Casi siempre significa que alguien tomó el cupo primero.
            log.warn("No se pudo crear la cita por WhatsApp: {}", e.getMessage());
            responder(empresa, c.getTelefono(),
                    "Uy, esa hora la acaban de tomar 😅 Le muestro las que quedan.");
            mostrarFranjas(empresa, c);
        }
    }

    // ---------------- Sin cupos: lista de espera ----------------

    private void ofrecerListaEspera(Empresa empresa, Conversacion c) {
        ZoneId zona = ZoneId.of(empresa.getZonaHoraria());
        LocalDate hoy = LocalDate.now(zona);

        ListaEspera espera = new ListaEspera();
        espera.setEmpresaId(empresa.getId());
        espera.setServicioId(c.getServicioId());
        espera.setDesde(hoy);
        espera.setHasta(hoy.plusDays(14));
        espera.setTelefono(c.getTelefono());
        espera.setNombre(c.getNombreCliente());
        listaEspera.save(espera);

        responder(empresa, c.getTelefono(), """
                Estamos llenas estos días 😅

                Pero la dejé anotada: si alguien cancela, usted es de las primeras
                a las que le escribo.""");
        c.reiniciar();
    }


    // ================= Cambiar o cancelar una cita =================

    /** Le muestra a la clienta sus citas próximas. */
    private void mostrarMisCitas(Empresa empresa, Conversacion c) {
        ZoneId zona = ZoneId.of(empresa.getZonaHoraria());
        List<Cita> mias = misCitas(empresa, c.getTelefono());

        if (mias.isEmpty()) {
            responder(empresa, c.getTelefono(),
                    "No le veo citas pendientes por acá 🤔\n\n"
                            + "Si quiere sacar una, escríbame *cita* y miramos horarios.");
            c.reiniciar();
            return;
        }

        List<Opcion> opciones = mias.stream()
                .map(x -> {
                    LocalDateTime inicio = LocalDateTime.ofInstant(x.getInicio(), zona);
                    return new Opcion("mia_" + x.getId(),
                            DIA.format(inicio) + " " + HORA.format(inicio),
                            nombreServicio(x.getServicioId()));
                }).toList();

        wa.lista(empresa.getWaPhoneNumberId(), empresa.getWaToken(), c.getTelefono(),
                "Estas son sus citas 👇 ¿Cuál quiere mover?",
                "Ver mis citas", "Mis citas", opciones);
        c.setPaso(PasoBot.ELIGIENDO_CITA);
    }

    private boolean esDe(String telefono, Cita cita) {
        if (cita.getClienteId() == null) return false;
        return clientesRepo.findById(cita.getClienteId())
                .map(cl -> cl.getTelefono().equals(telefono))
                .orElse(false);
    }

    private void recibirCita(Empresa empresa, Conversacion c, String seleccion) {
        if (seleccion == null || !seleccion.startsWith("mia_")) {
            mostrarMisCitas(empresa, c);
            return;
        }
        c.setCitaId(Long.valueOf(seleccion.substring(4)));

        // El plural incluye: no es ella sola resolviendo, es el salón con ella
        wa.botones(empresa.getWaPhoneNumberId(), empresa.getWaToken(), c.getTelefono(),
                "¿Qué hacemos con esa cita?",
                List.of(new Opcion("mover", "Cambiarle la hora", null),
                        new Opcion("anular", "Cancelarla", null),
                        new Opcion("nada", "Mejor la dejo así", null)));
        c.setPaso(PasoBot.QUE_HACER_CON_CITA);
    }

    private void recibirQueHacer(Empresa empresa, Conversacion c, String seleccion) {
        if (seleccion == null) { recibirCita(empresa, c, "mia_" + c.getCitaId()); return; }

        switch (seleccion) {
            case "anular" -> anular(empresa, c);
            case "mover" -> {
                Cita cita = citasRepo.findByIdAndEmpresaId(c.getCitaId(), empresa.getId())
                        .orElse(null);
                if (cita == null) {
                    responder(empresa, c.getTelefono(), "No encontré esa cita 🤔");
                    c.reiniciar();
                    return;
                }
                c.setServicioId(cita.getServicioId());
                mostrarFechasParaMover(empresa, c);
            }
            default -> {
                responder(empresa, c.getTelefono(), "Listo, ahí se la dejamos ✨");
                c.reiniciar();
            }
        }
    }

    private void anular(Empresa empresa, Conversacion c) {
        try {
            ContextoEmpresa.fijar(empresa.getId());
            citas.cambiarEstado(c.getCitaId(), EstadoCita.CANCELADA, null, null);            // Sin palomita verde: cancelar no es un logro que celebrar
            responder(empresa, c.getTelefono(), """
                    Listo, ya la cancelamos.

                    Cuando quiera volver, escríbame *cita* y miramos horarios.
                    ¡La esperamos pronto! 💕""");
        } catch (Exception e) {
            log.warn("No se pudo cancelar por WhatsApp: {}", e.getMessage());
            responder(empresa, c.getTelefono(),
                    "No la pude cancelar desde aquí. Ya le responde alguien para ayudarle.");
            c.setPaso(PasoBot.ESPERANDO_HUMANO);
            return;
        }
        c.reiniciar();
    }

    private void mostrarFechasParaMover(Empresa empresa, Conversacion c) {
        ZoneId zona = ZoneId.of(empresa.getZonaHoraria());
        LocalDate hoy = LocalDate.now(zona);

        List<Opcion> opciones = new ArrayList<>();
        for (int i = 0; i < 14 && opciones.size() < 9; i++) {
            LocalDate fecha = hoy.plusDays(i);
            if (!disponibilidad.cupos(c.getServicioId(), fecha, null).isEmpty()) {
                String etiqueta = i == 0 ? "Hoy" : i == 1 ? "Mañana" : DIA.format(fecha);
                opciones.add(new Opcion("nfec_" + fecha, etiqueta, null));
            }
        }
        if (opciones.isEmpty()) {
            responder(empresa, c.getTelefono(),
                    "Estamos llenas estos días 😅 Ya le responde alguien del salón.");
            c.setPaso(PasoBot.ESPERANDO_HUMANO);
            return;
        }

        wa.lista(empresa.getWaPhoneNumberId(), empresa.getWaToken(), c.getTelefono(),
                "¿Para qué día se la movemos?", "Ver días", "Días con campo", opciones);
        c.setPaso(PasoBot.REPROGRAMANDO_FECHA);
    }

    private void recibirFechaNueva(Empresa empresa, Conversacion c, String seleccion) {
        if (seleccion == null || !seleccion.startsWith("nfec_")) {
            mostrarFechasParaMover(empresa, c);
            return;
        }
        c.setFecha(LocalDate.parse(seleccion.substring(5)));

        List<Cupo> cupos = disponibilidad.cupos(c.getServicioId(), c.getFecha(), null);
        if (cupos.isEmpty()) { mostrarFechasParaMover(empresa, c); return; }

        // Una opción por hora, no por persona: si no, no caben.
        List<Opcion> opciones = new ArrayList<>();
        Set<LocalTime> horasVistas = new HashSet<>();
        for (Cupo cupo : cupos) {
            if (!horasVistas.add(cupo.inicio().toLocalTime())) continue;
            opciones.add(new Opcion("ncup_" + cupo.profesionalId() + "_" + cupo.inicio(),
                    HORA.format(cupo.inicio()), "La atiende " + cupo.profesionalNombre()));
            if (opciones.size() == 9) break;
        }

        wa.lista(empresa.getWaPhoneNumberId(), empresa.getWaToken(), c.getTelefono(),
                "Estas son las horas del " + DIA.format(c.getFecha()) + " 👇",
                "Ver horarios", "Horarios", opciones);
        c.setPaso(PasoBot.REPROGRAMANDO_CUPO);
    }

    private void recibirCupoNuevo(Empresa empresa, Conversacion c, String seleccion) {
        if (seleccion == null || !seleccion.startsWith("ncup_")) {
            recibirFechaNueva(empresa, c, "nfec_" + c.getFecha());
            return;
        }
        String[] partes = seleccion.split("_", 3);
        Long profesionalId = Long.valueOf(partes[1]);
        LocalDateTime inicio = LocalDateTime.parse(partes[2]);

        try {
            ContextoEmpresa.fijar(empresa.getId());
            citas.reprogramar(c.getCitaId(), profesionalId, inicio);

            responder(empresa, c.getTelefono(), """
                    ¡Listo, ya la movimos! ✨

                    📅 %s a las %s
                    👤 La atiende %s

                    Le recordamos el día anterior."""
                    .formatted(DIA.format(inicio), HORA.format(inicio),
                            nombreProfesional(profesionalId)));
            c.reiniciar();

        } catch (Exception e) {
            log.warn("No se pudo reprogramar por WhatsApp: {}", e.getMessage());
            responder(empresa, c.getTelefono(),
                    "Uy, esa hora la acaban de tomar 😅 Le muestro las que quedan.");
            recibirFechaNueva(empresa, c, "nfec_" + c.getFecha());
        }
    }

    // ================= Lista de espera cuando ninguno sirve =================

    private void preguntarRangoEspera(Empresa empresa, Conversacion c) {
        wa.botones(empresa.getWaPhoneNumberId(), empresa.getWaToken(), c.getTelefono(),
                "Yo le aviso apenas se desocupe algo 🙌\n\n¿Hasta cuándo le serviría?",
                List.of(new Opcion("esp_7", "Esta semana", null),
                        new Opcion("esp_15", "En quince días", null),
                        new Opcion("esp_30", "Cuando salga", null)));
        c.setPaso(PasoBot.ELIGIENDO_ESPERA);
    }

    private void recibirEspera(Empresa empresa, Conversacion c, String seleccion) {
        if (seleccion == null || !seleccion.startsWith("esp_")) {
            preguntarRangoEspera(empresa, c);
            return;
        }
        int dias = Integer.parseInt(seleccion.substring(4));
        ZoneId zona = ZoneId.of(empresa.getZonaHoraria());
        LocalDate hoy = LocalDate.now(zona);

        ListaEspera espera = new ListaEspera();
        espera.setEmpresaId(empresa.getId());
        espera.setServicioId(c.getServicioId());
        espera.setDesde(hoy);
        espera.setHasta(hoy.plusDays(dias));
        espera.setTelefono(c.getTelefono());
        espera.setNombre(c.getNombreCliente());
        listaEspera.save(espera);

        responder(empresa, c.getTelefono(), """
                Quedó anotada 🙌

                Si alguien cancela y le sirve la hora, usted es de las primeras
                a las que le escribo. Va por orden de llegada.""");
        c.reiniciar();
    }

    /** Al menos dos palabras de dos letras: nombre y apellido. */
    private boolean esNombreCompleto(String nombre) {
        if (nombre == null) return false;
        String[] partes = nombre.trim().split("\\s+");
        if (partes.length < 2) return false;
        return Arrays.stream(partes).filter(x -> x.length() >= 2).count() >= 2;
    }

    /** "maría fernanda LOPEZ" queda "María Fernanda Lopez". */
    private String limpiarNombre(String nombre) {
        return Arrays.stream(nombre.trim().split("\\s+"))
                .filter(x -> !x.isBlank())
                .map(x -> x.substring(0, 1).toUpperCase()
                        + (x.length() > 1 ? x.substring(1).toLowerCase() : ""))
                .reduce((a, b) -> a + " " + b)
                .orElse(nombre.trim());
    }

    private String nombreServicio(Long id) {
        return servicios.findById(id).map(Servicio::getNombre).orElse("Servicio");
    }

    private String nombreProfesional(Long id) {
        return profesionales.findById(id).map(Profesional::getNombre).orElse("");
    }

    private void responder(Empresa empresa, String telefono, String texto) {
        wa.texto(empresa.getWaPhoneNumberId(), empresa.getWaToken(), telefono, texto);
    }
}