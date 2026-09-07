package com.agenda.notificaciones;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacionRepositorio extends JpaRepository<Notificacion, Long> {
    boolean existsByCitaIdAndTipo(Long citaId, String tipo);
}
