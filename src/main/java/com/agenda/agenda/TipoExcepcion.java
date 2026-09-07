package com.agenda.agenda;

public enum TipoExcepcion {
    /** No viene en todo el día. */
    AUSENCIA,
    /** No está disponible entre dos horas: permiso, cita médica, almuerzo largo. */
    BLOQUEO,
    /** Ese día trabaja en otro rango. Reemplaza al horario base. */
    CAMBIO_HORARIO
}
