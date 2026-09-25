package com.agenda.agenda;

import java.time.Instant;

/**
 * La cita como la necesita el panel: con el nombre y el teléfono de la
 * clienta ya resueltos, y con lo que pagó.
 */
public record CitaVista(
        Long id,
        Long profesionalId,
        Long servicioId,
        Instant inicio,
        Instant fin,
        EstadoCita estado,
        OrigenCita origen,
        String clienteNombre,
        String clienteTelefono,
        MetodoPago metodoPago,
        Long valorCobradoCentavos,
        String notas
) {}