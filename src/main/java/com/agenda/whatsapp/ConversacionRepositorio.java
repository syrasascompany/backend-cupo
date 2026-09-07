package com.agenda.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversacionRepositorio extends JpaRepository<Conversacion, Long> {

    Optional<Conversacion> findByEmpresaIdAndTelefono(Long empresaId, String telefono);

    /** Conversaciones que hoy está atendiendo una persona, no el bot. */
    List<Conversacion> findByEmpresaIdAndPausadoHastaAfterOrderByUltimoMensajeDesc(
            Long empresaId, Instant momento);
}
