package com.agenda.agenda;

/** Con qué pagó la clienta. */
public enum MetodoPago {
    EFECTIVO,
    TRANSFERENCIA,
    TARJETA,
    OTRO;

    public String etiqueta() {
        return switch (this) {
            case EFECTIVO -> "Efectivo";
            case TRANSFERENCIA -> "Transferencia";
            case TARJETA -> "Tarjeta";
            case OTRO -> "Otro";
        };
    }
}