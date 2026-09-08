package com.agenda.whatsapp;

public enum PasoBot {
    INICIO,
    ELIGIENDO_SERVICIO,
    ELIGIENDO_FECHA,
    /** Escogiendo mañana, tarde o noche antes de ver las horas. */
    ELIGIENDO_FRANJA,
    ELIGIENDO_CUPO,
    PIDIENDO_NOMBRE,
    /** Escogiendo cuál de sus citas quiere mover o cancelar. */
    ELIGIENDO_CITA,
    /** Decidiendo qué hacer con la cita que escogió. */
    QUE_HACER_CON_CITA,
    /** Escogiendo la fecha nueva para reprogramar. */
    REPROGRAMANDO_FECHA,
    /** Escogiendo la hora nueva para reprogramar. */
    REPROGRAMANDO_CUPO,
    /** Decidiendo el rango de días para la lista de espera. */
    ELIGIENDO_ESPERA,
    ESPERANDO_HUMANO   // el bot se hizo a un lado; contesta el dueño
}