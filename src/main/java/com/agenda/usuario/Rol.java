package com.agenda.usuario;

public enum Rol {
    /** Tú. Crea empresas y define su plan. No pertenece a ninguna empresa. */
    SUPERADMIN,
    /** El dueño del negocio. Ve y maneja todo lo de su empresa. */
    ADMIN,
    /** La profesional. Solo ve sus propias citas y las marca como finalizadas. */
    TRABAJADORA
}
